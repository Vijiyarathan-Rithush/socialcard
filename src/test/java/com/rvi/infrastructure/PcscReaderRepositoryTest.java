package com.rvi.infrastructure;

import com.rvi.domain.ICardStatus;
import com.rvi.domain.NdefMessage;
import com.rvi.domain.Type2Tlv;
import com.rvi.exception.ReaderException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.smartcardio.ATR;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import org.junit.jupiter.api.Test;
import static com.rvi.exception.ReaderException.Reason.*;
import static org.junit.jupiter.api.Assertions.*;

class PcscReaderRepositoryTest
{
    @Test
    void readsInitializedBlankTagsAndWritesAnEmptyMessage() throws Exception
    {
        final Fixture fixture = new Fixture();
        fixture.channel.seed(new NdefMessage(new byte[0]));
        assertEquals(new ICardStatus.Ready(true, 496), fixture.repository.status("test"));
        assertEquals(0, fixture.repository.read("test").size());
        fixture.repository.write("test", new NdefMessage(new byte[0]));
        assertArrayEquals(new byte[]{3, 0, (byte) 0xFE, 0},
                Arrays.copyOfRange(fixture.channel.memory, 16, 20));
    }

    @Test
    void refusesReadOnlyAndUnsupportedCapabilitiesWithoutWriting() throws Exception
    {
        final Fixture readOnly = new Fixture();
        readOnly.channel.memory[15] = 0x0F;
        assertEquals(new ICardStatus.Ready(false, 496), readOnly.repository.status("test"));
        assertEquals(ACCESS_DENIED, assertThrows(ReaderException.class,
                () -> readOnly.repository.write("test", message(20))).reason());
        assertTrue(readOnly.channel.writtenPages.isEmpty());

        final Fixture unsupported = new Fixture();
        unsupported.channel.memory[14] = 0x12;
        assertEquals(new ICardStatus.Unsupported(), unsupported.repository.status("test"));
        assertEquals(UNSUPPORTED, assertThrows(ReaderException.class,
                () -> unsupported.repository.write("test", message(20))).reason());
        assertTrue(unsupported.channel.writtenPages.isEmpty());
    }

    @Test
    void mapsEveryStaticDataPageLockToTheCorrectBit()
    {
        for (int page = 4; page < 16; page++)
        {
            final Fixture fixture = new Fixture();
            fixture.channel.memory[page < 8 ? 10 : 11] = (byte) (1 << (page % 8));
            final NdefMessage target = message((page - 4) * 4 + 1);
            assertEquals(ACCESS_DENIED, assertThrows(ReaderException.class,
                    () -> fixture.repository.write("test", target)).reason());
            assertTrue(fixture.channel.writtenPages.isEmpty(), "Locked page " + page);
        }
    }

    @Test
    void mapsEveryDynamicLockThatCoversTheNdefDataArea()
    {
        for (int bit = 0; bit <= 6; bit++)
        {
            final Fixture fixture = new Fixture();
            fixture.channel.memory[130 * 4] = (byte) (1 << bit);
            final NdefMessage target = message((16 + bit * 16 - 4) * 4);
            assertEquals(ACCESS_DENIED, assertThrows(ReaderException.class,
                    () -> fixture.repository.write("test", target)).reason());
            assertTrue(fixture.channel.writtenPages.isEmpty(), "Dynamic lock bit " + bit);
        }
    }

    @Test
    void doesNotMistakeBlockLocksOrPagesOutsideTheNdefAreaForDataProtection() throws Exception
    {
        final Fixture fixture = new Fixture();
        fixture.channel.memory[10] = 0x0F; // Block-lock and CC-lock bits, without data-page locks.
        fixture.channel.memory[130 * 4] = (byte) 0x80; // Physical pages 128..129, outside CC data area.
        assertEquals(new ICardStatus.Ready(true, 496), fixture.repository.status("test"));
        fixture.repository.write("test", message(491));
        assertTrue(fixture.channel.writtenPages.stream().allMatch(page -> page <= 127));
    }

    @Test
    void refusesAStaticLockBeforeClearingTheExistingMessage() throws Exception
    {
        final Fixture fixture = new Fixture();
        final NdefMessage original = message(20);
        fixture.channel.seed(original);
        fixture.channel.memory[10] = 1 << 5;
        final ReaderException failure = assertThrows(ReaderException.class,
                () -> fixture.repository.write("test", message(30)));
        assertEquals(ACCESS_DENIED, failure.reason());
        assertTrue(fixture.channel.writtenPages.isEmpty());
        assertEquals(original, fixture.repository.read("test"));
        assertEquals(new ICardStatus.Ready(false, 496), fixture.repository.status("test"));
    }

    @Test
    void refusesDynamicLocksAndPasswordProtectionBeforeWriting()
    {
        final Fixture dynamic = new Fixture();
        dynamic.channel.memory[130 * 4] = 1;
        assertThrows(ReaderException.class, () -> dynamic.repository.write("test", message(80)));
        assertTrue(dynamic.channel.writtenPages.isEmpty());

        final Fixture password = new Fixture();
        password.channel.memory[131 * 4 + 3] = 5;
        assertThrows(ReaderException.class, () -> password.repository.write("test", message(20)));
        assertTrue(password.channel.writtenPages.isEmpty());
    }

    @Test
    void refusesUnreadableProtectionAndActiveMirroringBeforeWriting()
    {
        final Fixture inaccessible = new Fixture();
        inaccessible.channel.failReadPage = 130;
        assertThrows(ReaderException.class, () -> inaccessible.repository.write("test", message(20)));
        assertTrue(inaccessible.channel.writtenPages.isEmpty());

        final Fixture mirror = new Fixture();
        mirror.channel.memory[131 * 4] = 0x40;
        mirror.channel.memory[131 * 4 + 2] = 4;
        assertThrows(ReaderException.class, () -> mirror.repository.write("test", message(20)));
        assertTrue(mirror.channel.writtenPages.isEmpty());
    }

    @Test
    void checksTheEntireCapacityIncludingTheExtendedHeaderAndPadding() throws Exception
    {
        final Fixture fixture = new Fixture();
        final ReaderException failure = assertThrows(ReaderException.class,
                () -> fixture.repository.write("test", message(492)));
        assertEquals(CAPACITY_EXCEEDED, failure.reason());
        assertEquals(0, fixture.card.connections);
        assertTrue(fixture.channel.writtenPages.isEmpty());

        final NdefMessage full = message(491);
        fixture.repository.write("test", full);
        assertEquals(full, fixture.repository.read("test"));
        assertTrue(fixture.channel.writtenPages.stream().allMatch(page -> page >= 4 && page <= 127));
    }

    @Test
    void doesNotDestroyControlOrProprietaryTlvs()
    {
        final Fixture fixture = new Fixture();
        // The lock area is outside the CC data area, as allowed for Type 2 tags.
        System.arraycopy(new byte[]{1, 3, (byte) 0x88, 8, 0x66, 3, 0, (byte) 0xFE},
                0, fixture.channel.memory, 16, 8);
        final ReaderException failure = assertThrows(ReaderException.class,
                () -> fixture.repository.write("test", message(20)));
        assertEquals(UNSUPPORTED, failure.reason());
        assertTrue(fixture.channel.writtenPages.isEmpty());
    }

    @Test
    void reportsMalformedTagDataWithoutChangingTheTag()
    {
        final Fixture fixture = new Fixture();
        System.arraycopy(new byte[]{3, (byte) 255, 2, 0}, 0, fixture.channel.memory, 16, 4);
        final ReaderException failure = assertThrows(ReaderException.class,
                () -> fixture.repository.write("test", message(20)));
        assertEquals(INVALID_DATA, failure.reason());
        assertNotNull(failure.getCause());
        assertTrue(fixture.channel.writtenPages.isEmpty());
    }

    @Test
    void verifiesTheWrittenBytesBeforeReportingSuccess()
    {
        final Fixture fixture = new Fixture();
        fixture.channel.corruptCommit = true;
        final ReaderException failure = assertThrows(ReaderException.class,
                () -> fixture.repository.write("test", message(20)));
        assertEquals(VERIFICATION_FAILED, failure.reason());
        assertTrue(fixture.card.exclusiveStarted);
        assertTrue(fixture.card.exclusiveEnded);
        assertTrue(fixture.card.disconnected);
    }

    @Test
    void preservesThePrimaryFailureAndBothCleanupFailures()
    {
        final Fixture fixture = new Fixture();
        fixture.channel.failWritePage = 5;
        fixture.card.failEnd = true;
        fixture.card.failDisconnect = true;
        final ReaderException failure = assertThrows(ReaderException.class,
                () -> fixture.repository.write("test", message(20)));
        assertEquals(ACCESS_DENIED, failure.reason());
        assertTrue(failure.getMessage().contains("tag contents may be incomplete"));
        final ReaderException writeCause = assertInstanceOf(ReaderException.class, failure.getCause());
        assertEquals(ACCESS_DENIED, writeCause.reason());
        assertEquals(2, failure.getSuppressed().length);
        assertTrue(fixture.card.disconnected);
    }

    @Test
    void cleanupFailureDoesNotTurnAVerifiedWriteIntoAFailure()
    {
        final Fixture fixture = new Fixture();
        fixture.card.failEnd = true;
        fixture.card.failDisconnect = true;
        final NdefMessage message = message(20);
        assertDoesNotThrow(() -> fixture.repository.write("test", message));
        assertEquals(message, Type2Tlv.parse(Arrays.copyOfRange(fixture.channel.memory, 16, 512)));
    }

    @Test
    void closesTheCardIfExclusiveAccessCannotBeAcquired()
    {
        final Fixture fixture = new Fixture();
        fixture.card.failBegin = true;
        assertThrows(ReaderException.class, () -> fixture.repository.read("test"));
        assertTrue(fixture.card.disconnected);
        assertFalse(fixture.card.exclusiveEnded);
        assertTrue(fixture.channel.writtenPages.isEmpty());
    }

    @Test
    void reportsMissingCardAndMissingReaderDistinctly() throws Exception
    {
        final Fixture fixture = new Fixture();
        fixture.terminal.present = false;
        assertEquals(new ICardStatus.NoCard(), fixture.repository.status("test"));
        assertEquals(MISSING_CARD, assertThrows(ReaderException.class,
                () -> fixture.repository.read("test")).reason());
        assertEquals(MISSING_READER, assertThrows(ReaderException.class,
                () -> fixture.repository.read("another")).reason());
    }

    private static NdefMessage message(final int length)
    {
        final byte[] value = new byte[length];
        Arrays.fill(value, (byte) 0x41);
        return new NdefMessage(value);
    }

    private static final class Fixture
    {
        final MemoryChannel channel = new MemoryChannel();
        final FakeCard card = new FakeCard(channel);
        final FakeTerminal terminal = new FakeTerminal(card);
        final PcscReaderRepository repository = new PcscReaderRepository(() -> List.of(terminal));

        Fixture()
        {
            channel.card = card;
        }
    }

    private static final class MemoryChannel extends CardChannel
    {
        final byte[] memory = new byte[540];
        final List<Integer> writtenPages = new ArrayList<>();
        FakeCard card;
        int failReadPage = -1;
        int failWritePage = -1;
        boolean corruptCommit;

        MemoryChannel()
        {
            System.arraycopy(new byte[]{(byte) 0xE1, 0x10, 0x3E, 0}, 0, memory, 12, 4);
            memory[131 * 4 + 3] = (byte) 0xFF;
        }

        void seed(final NdefMessage message)
        {
            final byte[] encoded = Type2Tlv.encode(message);
            System.arraycopy(encoded, 0, memory, 16, encoded.length);
        }

        @Override
        public Card getCard()
        {
            return card;
        }

        @Override
        public int getChannelNumber()
        {
            return 0;
        }

        @Override
        public ResponseAPDU transmit(final CommandAPDU command)
        {
            assertTrue(card.exclusive, "Every card command must run inside the exclusive session.");
            final int page = command.getP2();
            if (command.getINS() == 0xB0)
            {
                if (page == failReadPage)
                {
                    return new ResponseAPDU(new byte[]{0x63, 0});
                }
                final byte[] response = Arrays.copyOfRange(memory, page * 4, page * 4 + 6);
                response[4] = (byte) 0x90;
                response[5] = 0;
                return new ResponseAPDU(response);
            }
            assertEquals(0xD6, command.getINS());
            writtenPages.add(page);
            if (page == failWritePage)
            {
                return new ResponseAPDU(new byte[]{0x63, 0});
            }
            System.arraycopy(command.getData(), 0, memory, page * 4, 4);
            if (corruptCommit && page == 4 && command.getData()[1] != 0)
            {
                memory[18] ^= 1;
            }
            return new ResponseAPDU(new byte[]{(byte) 0x90, 0});
        }

        @Override
        public int transmit(final ByteBuffer command, final ByteBuffer response)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close()
        {
        }
    }

    private static final class FakeCard extends Card
    {
        final CardChannel channel;
        boolean exclusive;
        boolean exclusiveStarted;
        boolean exclusiveEnded;
        boolean disconnected;
        boolean failBegin;
        boolean failEnd;
        boolean failDisconnect;
        int connections;

        FakeCard(final CardChannel channel)
        {
            this.channel = channel;
        }

        @Override
        public ATR getATR()
        {
            return new ATR(new byte[]{0x3B, 0});
        }

        @Override
        public String getProtocol()
        {
            return "T=1";
        }

        @Override
        public CardChannel getBasicChannel()
        {
            return channel;
        }

        @Override
        public CardChannel openLogicalChannel()
        {
            return channel;
        }

        @Override
        public void beginExclusive() throws CardException
        {
            if (failBegin)
            {
                throw new CardException("begin failed");
            }
            exclusive = true;
            exclusiveStarted = true;
        }

        @Override
        public void endExclusive() throws CardException
        {
            exclusiveEnded = true;
            exclusive = false;
            if (failEnd)
            {
                throw new CardException("end failed");
            }
        }

        @Override
        public byte[] transmitControlCommand(final int code, final byte[] command)
        {
            return new byte[0];
        }

        @Override
        public void disconnect(final boolean reset) throws CardException
        {
            disconnected = true;
            if (failDisconnect)
            {
                throw new CardException("disconnect failed");
            }
        }
    }

    private static final class FakeTerminal extends CardTerminal
    {
        final FakeCard card;
        boolean present = true;

        FakeTerminal(final FakeCard card)
        {
            this.card = card;
        }

        @Override
        public String getName()
        {
            return "test";
        }

        @Override
        public Card connect(final String protocol)
        {
            card.connections++;
            return card;
        }

        @Override
        public boolean isCardPresent()
        {
            return present;
        }

        @Override
        public boolean waitForCardPresent(final long timeout)
        {
            return present;
        }

        @Override
        public boolean waitForCardAbsent(final long timeout)
        {
            return !present;
        }
    }
}
