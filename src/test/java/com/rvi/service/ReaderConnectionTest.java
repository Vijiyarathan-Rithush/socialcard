package com.rvi.service;

import com.rvi.application.creator.NDefMessageCreator;
import com.rvi.application.creator.NDefTextCreator;
import com.rvi.application.exception.ReaderServiceException;
import com.rvi.domain.NDefMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CardTerminals;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.smartcardio.ATR;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class ReaderConnectionTest
{
    @Test
    void listsExactReaderNamesWithoutConnectingAndReportsDiscoveryFailures()
    {
        final FakeTerminal first = new FakeTerminal("First");
        final FakeTerminal second = new FakeTerminal("Second");
        final FakeTerminals terminals = new FakeTerminals(List.of(first, second));
        final List<String> names = ReaderService.listReaders(() -> terminals);
        assertEquals(List.of("First", "Second"), names);
        assertThrows(UnsupportedOperationException.class, () -> names.clear());
        assertEquals(0, first.connects);
        assertEquals(0, second.connects);
        assertTrue(ReaderService.listReaders(() -> new FakeTerminals(List.of())).isEmpty());
        terminals.failure = new CardException("PC/SC unavailable");
        assertThrows(ReaderServiceException.class, () -> ReaderService.listReaders(() -> terminals));
    }

    @Test
    void factoriesCreateServicesWithoutConnectingToHardware()
    {
        try (final IReaderService service = ReaderService.create())
        {
            assertNotNull(service.encodeURI("https://a.ch"));
            assertThrows(ReaderServiceException.class, service::read);
        }

        try (final IReaderService service = ReaderService.create("Example Reader"))
        {
            assertNotNull(service);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingReaderNames(final String readerName)
    {
        assertThrows(ReaderServiceException.class, () -> ReaderService.create(readerName));
    }

    @Test
    void rejectsReadAndWriteBeforeConnect()
    {
        final FakeTerminals terminals = new FakeTerminals(List.of());
        final ReaderService service = new ReaderService(() -> terminals, null);

        assertThrows(ReaderServiceException.class, service::read);
        assertThrows(ReaderServiceException.class, () -> service.write(message()));
        service.disconnect();
        service.close();
        assertEquals(0, terminals.lists);
    }

    @Test
    void reportsNoReadersAndNoCardWithoutWaiting()
    {
        final ReaderService missingReader = new ReaderService(() -> new FakeTerminals(List.of()), null);
        assertThrows(ReaderServiceException.class, missingReader::connect);

        final FakeTerminal terminal = new FakeTerminal("Reader");
        terminal.present = false;
        final ReaderService missingCard = service(terminal);
        assertThrows(ReaderServiceException.class, missingCard::connect);
        assertEquals(0, terminal.connects);
    }

    @Test
    void selectsTheFirstReaderByDefaultAndTheNamedReaderExplicitly()
    {
        final FakeTerminal first = new FakeTerminal("First");
        final FakeTerminal second = new FakeTerminal("Second");
        final FakeTerminals terminals = new FakeTerminals(List.of(first, second));

        try (final IReaderService service = new ReaderService(() -> terminals, null))
        {
            service.connect();
            assertEquals(1, first.connects);
            assertEquals(0, second.connects);
        }

        try (final IReaderService service = new ReaderService(() -> terminals, "Second"))
        {
            service.connect();
            assertEquals(1, second.connects);
        }

        final IReaderService missing = new ReaderService(() -> terminals, "Missing");
        assertThrows(ReaderServiceException.class, missing::connect);
        assertEquals(1, first.connects);
        assertEquals(1, second.connects);
    }

    @Test
    void connectsWritesReadsAndClosesThroughThePublicInterface()
    {
        final FakeTerminal terminal = new FakeTerminal("Reader");
        final NDefMessage message = message();

        try (final IReaderService service = service(terminal))
        {
            service.connect();
            service.write(message);

            assertArrayEquals(message.data(), service.read().data());
            assertEquals("*", terminal.protocol);
        }

        assertEquals(1, terminal.card.disconnects);
        assertFalse(terminal.card.reset);
    }

    @Test
    void rejectsDoubleConnectAndAllowsReconnectAfterDisconnect()
    {
        final FakeTerminal terminal = new FakeTerminal("Reader");
        final IReaderService service = service(terminal);

        service.connect();
        assertThrows(ReaderServiceException.class, service::connect);
        assertEquals(1, terminal.connects);
        service.disconnect();
        service.disconnect();
        assertEquals(1, terminal.card.disconnects);
        assertThrows(ReaderServiceException.class, service::read);

        service.connect();
        service.close();
        assertEquals(2, terminal.connects);
        assertEquals(2, terminal.card.disconnects);
    }

    @Test
    void closesWhenTheBodyThrows()
    {
        final FakeTerminal terminal = new FakeTerminal("Reader");
        final IllegalStateException failure = new IllegalStateException("Example failure");

        final IllegalStateException actual = assertThrows(IllegalStateException.class, () ->
        {
            try (final IReaderService service = service(terminal))
            {
                service.connect();
                throw failure;
            }
        });

        assertSame(failure, actual);
        assertEquals(1, terminal.card.disconnects);
    }

    @Test
    void preservesConnectFailureAndAllowsRetry()
    {
        final FakeTerminal terminal = new FakeTerminal("Reader");
        final CardException failure = new CardException("Card removed");
        terminal.failure = failure;
        final IReaderService service = service(terminal);

        final ReaderServiceException actual = assertThrows(ReaderServiceException.class, service::connect);
        assertSame(failure, actual.getCause());
        assertThrows(ReaderServiceException.class, service::read);
        service.disconnect();
        assertEquals(0, terminal.card.disconnects);

        terminal.failure = null;
        service.connect();
        service.close();
        assertEquals(1, terminal.card.disconnects);
    }

    @Test
    void preservesEnumerationAndPresenceFailures()
    {
        final FakeTerminals terminals = new FakeTerminals(List.of());
        terminals.failure = new CardException("Reader enumeration failed");
        final IReaderService unavailable = new ReaderService(() -> terminals, null);
        assertSame(terminals.failure, assertThrows(ReaderServiceException.class, unavailable::connect).getCause());

        final FakeTerminal terminal = new FakeTerminal("Reader");
        terminal.presenceFailure = new CardException("Reader removed");
        final IReaderService disconnected = service(terminal);
        assertSame(terminal.presenceFailure, assertThrows(ReaderServiceException.class, disconnected::connect).getCause());
        assertEquals(0, terminal.connects);
    }

    @Test
    void cleansUpAPartiallyInitializedConnection()
    {
        final FakeTerminal terminal = new FakeTerminal("Reader");
        terminal.card.channelFailure = new IllegalStateException("Channel unavailable");
        terminal.card.disconnectFailure = new CardException("Cleanup failed");
        final IReaderService service = service(terminal);

        final ReaderServiceException failure = assertThrows(ReaderServiceException.class, service::connect);

        assertSame(terminal.card.channelFailure, failure.getCause());
        assertArrayEquals(new Throwable[] {terminal.card.disconnectFailure}, failure.getCause().getSuppressed());
        assertEquals(1, terminal.card.disconnects);
        assertThrows(ReaderServiceException.class, service::read);
    }

    @Test
    void clearsTheServiceStateAfterADisconnectFailure()
    {
        final FakeTerminal terminal = new FakeTerminal("Reader");
        final IReaderService service = service(terminal);
        service.connect();
        terminal.card.disconnectFailure = new CardException("Disconnect failed");

        final ReaderServiceException failure = assertThrows(ReaderServiceException.class, service::disconnect);
        assertSame(terminal.card.disconnectFailure, failure.getCause());
        assertThrows(ReaderServiceException.class, service::read);
        service.close();
        assertEquals(1, terminal.card.disconnects);
    }

    private ReaderService service(final FakeTerminal terminal)
    {
        return new ReaderService(() -> new FakeTerminals(List.of(terminal)), null);
    }

    private NDefMessage message()
    {
        return new NDefMessageCreator().add(new NDefTextCreator().text("Hello").language("en").build()).build();
    }

    private static final class FakeTerminals extends CardTerminals
    {
        private final List<CardTerminal> terminals;
        private int lists;
        private CardException failure;

        private FakeTerminals(final List<CardTerminal> terminals)
        {
            this.terminals = terminals;
        }

        @Override
        public List<CardTerminal> list(final State state) throws CardException
        {
            lists++;

            if (failure != null)
            {
                throw failure;
            }

            return terminals;
        }

        @Override
        public boolean waitForChange(final long timeout)
        {
            throw new AssertionError("Connecting must not wait for a card");
        }
    }

    private static final class FakeTerminal extends CardTerminal
    {
        private final String name;
        private final FakeCard card = new FakeCard();
        private boolean present = true;
        private int connects;
        private String protocol;
        private CardException failure;
        private CardException presenceFailure;

        private FakeTerminal(final String name)
        {
            this.name = name;
        }

        @Override
        public String getName()
        {
            return name;
        }

        @Override
        public Card connect(final String protocol) throws CardException
        {
            connects++;
            this.protocol = protocol;

            if (failure != null)
            {
                throw failure;
            }

            return card;
        }

        @Override
        public boolean isCardPresent() throws CardException
        {
            if (presenceFailure != null)
            {
                throw presenceFailure;
            }

            return present;
        }

        @Override
        public boolean waitForCardPresent(final long timeout)
        {
            throw new AssertionError("Connecting must not wait for a card");
        }

        @Override
        public boolean waitForCardAbsent(final long timeout)
        {
            throw new AssertionError("Disconnecting must not wait for a card");
        }
    }

    private static final class FakeCard extends Card
    {
        private final FakeChannel channel = new FakeChannel(this);
        private int disconnects;
        private boolean reset;
        private CardException disconnectFailure;
        private RuntimeException channelFailure;

        @Override
        public ATR getATR()
        {
            return new ATR(new byte[] {0x3B, 0});
        }

        @Override
        public String getProtocol()
        {
            return "T=1";
        }

        @Override
        public CardChannel getBasicChannel()
        {
            if (channelFailure != null)
            {
                throw channelFailure;
            }

            return channel;
        }

        @Override
        public CardChannel openLogicalChannel()
        {
            throw new AssertionError("Only the basic channel should be used");
        }

        @Override
        public void beginExclusive()
        {
        }

        @Override
        public void endExclusive()
        {
        }

        @Override
        public byte[] transmitControlCommand(final int code, final byte[] command)
        {
            throw new AssertionError("No control command is expected");
        }

        @Override
        public void disconnect(final boolean reset) throws CardException
        {
            disconnects++;
            this.reset = reset;

            if (disconnectFailure != null)
            {
                throw disconnectFailure;
            }
        }
    }

    private static final class FakeChannel extends CardChannel
    {
        private final Card card;
        private final byte[] memory = new byte[520];

        private FakeChannel(final Card card)
        {
            this.card = card;
            memory[12] = (byte) 0xE1;
            memory[13] = 0x10;
            memory[14] = 0x3E;
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
            final int offset = command.getP2() * 4;
            final byte[] data;

            if (command.getINS() == 0xB0)
            {
                data = Arrays.copyOfRange(memory, offset, offset + 4);
            }
            else if (command.getINS() == 0xD6)
            {
                System.arraycopy(command.getData(), 0, memory, offset, 4);
                data = new byte[0];
            }
            else
            {
                throw new AssertionError("Unexpected command");
            }

            final byte[] response = Arrays.copyOf(data, data.length + 2);
            response[data.length] = (byte) 0x90;
            return new ResponseAPDU(response);
        }

        @Override
        public int transmit(final ByteBuffer command, final ByteBuffer response)
        {
            throw new AssertionError("No buffer command is expected");
        }

        @Override
        public void close()
        {
            throw new AssertionError("The service should disconnect the card");
        }
    }
}
