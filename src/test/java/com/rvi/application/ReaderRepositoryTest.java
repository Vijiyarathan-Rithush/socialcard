package com.rvi.application;

import com.rvi.application.ReaderRepository;
import com.rvi.application.NDefEncoder;
import com.rvi.domain.NDefMessage;
import com.rvi.domain.exception.NDefMessageException;
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
        return new NDefMessage(new byte[] {
                3, 9, (byte) 0xD1, 1, 5, 0x55, 4,
                0x61, 0x2E, 0x63, 0x68, (byte) 0xFE
        });
    }

    @Test
    void rejectsNullChannel()
    {
        assertThrows(NullPointerException.class, () -> new ReaderRepository(null));
    }

    @Test
    void readsUsingExpectedCommands() throws CardException
    {
        byte[] expectedData = message().data();
        channel.load(expectedData);

        byte[] actualData = repository.read().data();

        assertArrayEquals(expectedData, actualData);
        assertEquals(3, channel.commands.size());

        for (int index = 0; index < 3; index++)
        {
            byte page = (byte) (4 + index);
            byte[] expectedCommand = {(byte) 0xFF, (byte) 0xB0, 0, page, 4};
            byte[] actualCommand = channel.commands.get(index).getBytes();

            assertArrayEquals(expectedCommand, actualCommand);
        }
    }

    @Test
    void writesBodyBeforeLengthAndVerifies() throws CardException
    {
        NDefMessage input = message();
        byte[] expectedInitialPage = {3, 0, (byte) 0xFE, 0};
        byte[] expectedFinalPage = Arrays.copyOf(input.data(), 4);

        repository.write(input);

        assertEquals(7, channel.commands.size());

        List<CommandAPDU> writeCommands = channel.commands.subList(0, 4);
        List<CommandAPDU> readCommands = channel.commands.subList(4, 7);
        List<Integer> actualPages = writeCommands.stream().map(CommandAPDU::getP2).toList();
        byte[] actualInitialPage = writeCommands.getFirst().getData();
        byte[] actualFinalPage = writeCommands.getLast().getData();
        byte[] actualStoredData = Arrays.copyOfRange(channel.memory, 16, 28);

        assertEquals(List.of(4, 5, 6, 4), actualPages);
        assertArrayEquals(expectedInitialPage, actualInitialPage);
        assertArrayEquals(expectedFinalPage, actualFinalPage);
        assertArrayEquals(input.data(), actualStoredData);

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
        Arrays.fill(channel.memory, (byte) 0x7F);

        repository.write(input);

        CommandAPDU lastPageCommand = channel.commands.get(3);
        byte[] actualLastPage = lastPageCommand.getData();
        byte[] actualMessage = repository.read().data();

        assertEquals(0xD6, lastPageCommand.getINS());
        assertEquals(7, lastPageCommand.getP2());
        assertArrayEquals(expectedLastPage, actualLastPage);
        assertArrayEquals(input.data(), actualMessage);
    }

    @Test
    void handlesExtendedMessageAtMaximumCapacity() throws CardException
    {
        String url = "https://a.ch/" + "a".repeat(478);
        NDefMessage input = new NDefEncoder().encodeURI(url);

        repository.write(input);

        byte[] actual = repository.read().data();
        int highestPage = channel.commands.stream().mapToInt(CommandAPDU::getP2).max().orElseThrow();

        assertArrayEquals(input.data(), actual);
        assertEquals(127, highestPage);

        for (CommandAPDU command : channel.commands)
        {
            int page = command.getP2();

            assertTrue(page >= 4 && page <= 129);
        }
    }

    @Test
    void publishesExtendedLengthOnlyAfterWritingTheBody() throws CardException
    {
        NDefMessage input = new NDefEncoder().encodeURI("https://a.ch/" + "x".repeat(478));
        Arrays.fill(channel.memory, 512, 520, (byte) 0x7F);
        repository.write(input);
        final var writes = channel.commands.stream().filter(command -> command.getINS() == 0xD6).toList();
        assertArrayEquals(new byte[] {3, 0, (byte) 0xFE, 0}, writes.getFirst().getData());
        assertArrayEquals(Arrays.copyOf(input.data(), 4), writes.getLast().getData());
        assertEquals(4, writes.getLast().getP2());
        assertArrayEquals(new byte[] {127, 127, 127, 127, 127, 127, 127, 127},
                Arrays.copyOfRange(channel.memory, 512, 520));
    }

    @Test
    void rejectsExtendedLengthOutsideCapacityWithoutReadingBody()
    {
        channel.load(new byte[] {3, (byte) 255, (byte) 255, (byte) 255});
        assertThrows(CardException.class, repository::read);
        assertEquals(1, channel.commands.size());
    }

    @Test
    void rejectsNullBeforeCommunication()
    {
        assertThrows(NullPointerException.class, () -> repository.write(null));
        assertTrue(channel.commands.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"0,9", "3,0", "3,255"})
    void rejectsUnsupportedFirstPage(int type, int length)
    {
        byte[] firstPage = {(byte) type, (byte) length, 0, 0};
        channel.load(firstPage);

        assertThrows(CardException.class, repository::read);
        assertEquals(1, channel.commands.size());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 5})
    void rejectsUnexpectedResponseLength(int length)
    {
        channel.readLength = length;

        assertThrows(CardException.class, repository::read);
        assertEquals(1, channel.commands.size());
    }

    @ParameterizedTest
    @ValueSource(ints = {0x6300, 0x6982, 0x6A82})
    void stopsOnReadStatusFailure(int status)
    {
        channel.failAt = 1;
        channel.status = status;

        assertThrows(CardException.class, repository::read);
        assertEquals(1, channel.commands.size());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4})
    void stopsOnWriteStatusFailure(int position)
    {
        NDefMessage input = message();
        channel.failAt = position;

        assertThrows(CardException.class, () -> repository.write(input));
        assertEquals(position, channel.commands.size());

        for (CommandAPDU command : channel.commands)
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

        CardException actual = assertThrows(CardException.class, () -> repository.write(input));

        assertSame(expected, actual);
        assertEquals(2, channel.commands.size());
    }

    @Test
    void rejectsPartialRead()
    {
        channel.load(message().data());
        channel.failAt = 2;

        assertThrows(CardException.class, repository::read);
        assertEquals(2, channel.commands.size());
    }

    @Test
    void failsWhenVerificationCannotRead()
    {
        NDefMessage input = message();
        channel.failAt = 5;

        assertThrows(CardException.class, () -> repository.write(input));
        assertEquals(5, channel.commands.size());
    }

    @Test
    void detectsDifferentReadBackData()
    {
        NDefMessage input = message();
        channel.corrupt = true;

        CardException actual = assertThrows(CardException.class, () -> repository.write(input));

        assertEquals("Verification failed", actual.getMessage());
    }

    @Test
    void rejectsCorruptedStoredMessage()
    {
        byte[] data = message().data();
        data[11] = 0;
        channel.load(data);

        assertThrows(NDefMessageException.class, repository::read);
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