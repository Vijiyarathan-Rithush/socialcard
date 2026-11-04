package com.rvi.application;

import com.rvi.application.ReaderRepository;
import com.rvi.application.NDefEncoder;
import com.rvi.domain.NDefMessage;
import com.rvi.domain.exception.NDefMessageException;
import com.rvi.application.exception.CardCapacityException;
import com.rvi.application.exception.ReaderServiceException;
import com.rvi.application.exception.TlvException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.smartcardio.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReaderRepositoryTest
{
    private final FakeChannel channel = new FakeChannel();
    private final ReaderRepository repository = new ReaderRepository(channel);

    private NDefMessage message()
    {
        return new NDefMessage(new byte[] {(byte) 0xD1, 1, 5, 0x55, 4, 0x61, 0x2E, 0x63, 0x68});
    }

    @Test
    void rejectsNullChannel()
    {
        assertThrows(ReaderServiceException.class, () -> new ReaderRepository(null));
    }

    @Test
    void readsUsingExpectedCommands() throws CardException
    {
        byte[] expectedData = message().data();
        channel.load(new TlvCodec().encode(message()));

        byte[] actualData = repository.read().data();

        assertArrayEquals(expectedData, actualData);
        assertEquals(4, channel.commands.size());
        assertEquals(3, channel.commands.getFirst().getP2());

        for (int index = 0; index < 3; index++)
        {
            byte page = (byte) (4 + index);
            byte[] expectedCommand = {(byte) 0xFF, (byte) 0xB0, 0, page, 4};
            byte[] actualCommand = channel.commands.get(index + 1).getBytes();

            assertArrayEquals(expectedCommand, actualCommand);
        }
    }

    @Test
    void writesBodyBeforeLengthAndVerifies() throws CardException
    {
        NDefMessage input = message();
        byte[] expectedInitialPage = {3, 0, (byte) 0xFE, 0};
        byte[] encoded = new TlvCodec().encode(input);
        byte[] expectedFinalPage = Arrays.copyOf(encoded, 4);

        repository.write(input);

        assertEquals(9, channel.commands.size());

        List<CommandAPDU> writeCommands = channel.commands.subList(1, 5);
        List<CommandAPDU> readCommands = channel.commands.subList(5, 9);
        List<Integer> actualPages = writeCommands.stream().map(CommandAPDU::getP2).toList();
        byte[] actualInitialPage = writeCommands.getFirst().getData();
        byte[] actualFinalPage = writeCommands.getLast().getData();
        byte[] actualStoredData = Arrays.copyOfRange(channel.memory, 16, 28);

        assertEquals(List.of(4, 5, 6, 4), actualPages);
        assertArrayEquals(expectedInitialPage, actualInitialPage);
        assertArrayEquals(expectedFinalPage, actualFinalPage);
        assertArrayEquals(encoded, actualStoredData);

        for (CommandAPDU command : writeCommands)
        {
            assertEquals(0xFF, command.getCLA());
            assertEquals(0xD6, command.getINS());
            assertEquals(0, command.getP1());
            assertEquals(4, command.getNc());
        }

        for (CommandAPDU command : readCommands)
        {
            assertEquals(0xB0, command.getINS());
        }
    }

    @Test
    void padsLastPageWithoutChangingMessage() throws CardException
    {
        NDefMessage input = new NDefEncoder().encodeURI("https://a.ch/x");
        byte[] expectedLastPage = {0x78, (byte) 0xFE, 0, 0};
        Arrays.fill(channel.memory, 16, channel.memory.length, (byte) 0x7F);

        repository.write(input);

        CommandAPDU lastPageCommand = channel.commands.get(4);
        byte[] actualLastPage = lastPageCommand.getData();
        byte[] actualMessage = repository.read().data();

        assertEquals(0xD6, lastPageCommand.getINS());
        assertEquals(7, lastPageCommand.getP2());
        assertArrayEquals(expectedLastPage, actualLastPage);
        assertArrayEquals(input.data(), actualMessage);
    }

    @Test
    void handlesShortTlvNearTheLengthBoundary() throws CardException
    {
        String url = "https://a.ch/" + "a".repeat(244);
        NDefMessage input = new NDefEncoder().encodeURI(url);

        repository.write(input);

        byte[] actual = repository.read().data();
        int highestPage = channel.commands.stream().mapToInt(CommandAPDU::getP2).max().orElseThrow();

        assertArrayEquals(input.data(), actual);
        assertEquals(68, highestPage);

        for (CommandAPDU command : channel.commands)
        {
            int page = command.getP2();

            assertTrue(page >= 3 && page <= 127);
        }
    }

    @Test
    void rejectsNullBeforeCommunication()
    {
        assertThrows(ReaderServiceException.class, () -> repository.write(null));
        assertTrue(channel.commands.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"0,9", "3,0", "3,255"})
    void rejectsUnsupportedFirstPage(int type, int length)
    {
        byte[] firstPage = {(byte) type, (byte) length, 0, 0};
        channel.load(firstPage);

        assertThrows(TlvException.class, repository::read);
        assertTrue(channel.commands.size() <= 2);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 5})
    void rejectsUnexpectedResponseLength(int length)
    {
        channel.readLength = length;

        assertThrows(ReaderServiceException.class, repository::read);
        assertEquals(1, channel.commands.size());
    }

    @ParameterizedTest
    @ValueSource(ints = {0x6300, 0x6982, 0x6A82})
    void stopsOnReadStatusFailure(int status)
    {
        channel.failAt = 1;
        channel.status = status;

        assertThrows(ReaderServiceException.class, repository::read);
        assertEquals(1, channel.commands.size());
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 5})
    void stopsOnWriteStatusFailure(int position)
    {
        NDefMessage input = message();
        channel.failAt = position;

        assertThrows(ReaderServiceException.class, () -> repository.write(input));
        assertEquals(position, channel.commands.size());

        for (CommandAPDU command : channel.commands.subList(1, channel.commands.size()))
        {
            assertEquals(0xD6, command.getINS());
        }
    }

    @Test
    void propagatesDisconnectionWithoutRetry()
    {
        NDefMessage input = message();
        CardException expected = channel.disconnection;
        channel.disconnectAt = 2;

        ReaderServiceException actual = assertThrows(ReaderServiceException.class, () -> repository.write(input));

        assertSame(expected, actual.getCause());
        assertEquals(2, channel.commands.size());
    }

    @Test
    void rejectsPartialRead()
    {
        channel.load(new TlvCodec().encode(message()));
        channel.failAt = 2;

        assertThrows(ReaderServiceException.class, repository::read);
        assertEquals(2, channel.commands.size());
    }

    @Test
    void failsWhenVerificationCannotRead()
    {
        NDefMessage input = message();
        channel.failAt = 6;

        assertThrows(ReaderServiceException.class, () -> repository.write(input));
        assertEquals(6, channel.commands.size());
    }

    @Test
    void detectsDifferentReadBackData()
    {
        NDefMessage input = message();
        channel.corrupt = true;

        ReaderServiceException actual = assertThrows(ReaderServiceException.class, () -> repository.write(input));

        assertEquals("Verification failed", actual.getMessage());
    }

    @Test
    void rejectsCorruptedStoredMessage()
    {
        final byte[] encoded = new TlvCodec().encode(message());
        encoded[2] = 0;
        channel.load(encoded);

        assertThrows(NDefMessageException.class, repository::read);
    }

    @Test
    void roundTripsTheLargestMessageWithTerminator() throws CardException
    {
        final NDefMessage input = new NDefEncoder().encodeURI("https://a.ch/" + "x".repeat(478));

        assertEquals(491, input.data().length);
        repository.write(input);

        assertArrayEquals(input.data(), repository.read().data());
        assertEquals(127, channel.commands.stream().mapToInt(CommandAPDU::getP2).max().orElseThrow());
    }

    @Test
    void rejectsOverflowBeforeAnyWrite()
    {
        final NDefMessage input = new NDefEncoder().encodeURI("https://a.ch/" + "x".repeat(479));

        assertThrows(CardCapacityException.class, () -> repository.write(input));
        assertEquals(1, channel.commands.size());
        assertEquals(0xB0, channel.commands.getFirst().getINS());
    }

    @Test
    void rejectsOversizedStoredLengthBeforeReadingTheBody()
    {
        channel.load(new byte[] {3, (byte) 0xFF, 1, (byte) 0xF4});

        assertThrows(CardCapacityException.class, repository::read);
        assertEquals(2, channel.commands.size());
    }

    @ParameterizedTest
    @ValueSource(ints = {0x0F, 0x01, 0xF0})
    void rejectsRestrictedAccessBeforeWriting(final int access)
    {
        channel.memory[15] = (byte) access;

        assertThrows(ReaderServiceException.class, () -> repository.write(message()));
        assertEquals(1, channel.commands.size());
    }

    @Test
    void allowsReadingAReadOnlyTag() throws CardException
    {
        channel.memory[15] = 0x0F;
        channel.load(new TlvCodec().encode(message()));

        assertArrayEquals(message().data(), repository.read().data());
    }

    @ParameterizedTest
    @CsvSource({"12,0", "13,32", "14,0", "14,109"})
    void rejectsInvalidCapabilityContainer(final int offset, final int value)
    {
        channel.memory[offset] = (byte) value;

        assertThrows(ReaderServiceException.class, () -> repository.write(message()));
        assertEquals(1, channel.commands.size());
    }

    private static class FakeChannel extends CardChannel
    {
        private final byte[] memory = new byte[520];
        private final List<CommandAPDU> commands = new ArrayList<>();
        private final CardException disconnection = new CardException("Card removed");

        private int failAt = -1;
        private int disconnectAt = -1;
        private int status = 0x6982;
        private int readLength = 4;
        private boolean corrupt;

        FakeChannel()
        {
            memory[12] = (byte) 0xE1;
            memory[13] = 0x10;
            memory[14] = 0x3E;
        }

        void load(byte[] data)
        {
            System.arraycopy(data, 0, memory, 16, data.length);
        }

        @Override
        public ResponseAPDU transmit(CommandAPDU command) throws CardException
        {
            commands.add(command);
            int commandNumber = commands.size();

            if (commandNumber == disconnectAt)
            {
                throw disconnection;
            }

            if (commandNumber == failAt)
            {
                return response(new byte[0], status);
            }

            int page = command.getP2();
            int offset = page * 4;
            int instruction = command.getINS();

            if (instruction == 0xD6)
            {
                byte[] data = command.getData();
                System.arraycopy(data, 0, memory, offset, data.length);

                return response(new byte[0], 0x9000);
            }

            if (instruction == 0xB0)
            {
                byte[] data = Arrays.copyOfRange(memory, offset, offset + readLength);

                if (corrupt && page == 5 && data.length == 4)
                {
                    data[3] ^= 1;
                }

                return response(data, 0x9000);
            }

            throw new AssertionError("Unexpected instruction: " + instruction);
        }

        private ResponseAPDU response(byte[] data, int status)
        {
            byte[] result = Arrays.copyOf(data, data.length + 2);
            result[data.length] = (byte) (status >>> 8);
            result[data.length + 1] = (byte) status;

            return new ResponseAPDU(result);
        }

        @Override
        public Card getCard()
        {
            return null;
        }

        @Override
        public int getChannelNumber()
        {
            return 0;
        }

        @Override
        public void close()
        {
        }

        @Override
        public int transmit(ByteBuffer command, ByteBuffer response)
        {
            throw new UnsupportedOperationException();
        }
    }
}