package com.rvi.application;

import org.junit.jupiter.api.Test;

import javax.smartcardio.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ReaderListenerTest
{
    @Test
    void ignoresCardsUntilActivated()
    {
        final FakeTerminal terminal = new FakeTerminal();
        final ReaderListener listener = new ReaderListener(status -> {});
        listener.onCardInserted(terminal);
        assertEquals(0, terminal.connections);
    }

    @Test
    void countsVerifiedCardsAndRecoversAfterFailure()
    {
        final List<ReaderStatus> updates = new CopyOnWriteArrayList<>();
        final ReaderListener listener = new ReaderListener(updates::add);
        final FakeTerminal terminal = new FakeTerminal();
        listener.activate(new NDefEncoder().encodeURI("https://example.com"));
        terminal.fail = true;
        listener.onCardInserted(terminal);
        assertEquals(ReaderStatus.State.ERROR, updates.getLast().state());
        assertEquals(0, updates.getLast().successfulCards());
        terminal.fail = false;
        listener.onCardRemoved(terminal);
        listener.onCardInserted(terminal);
        assertEquals(ReaderStatus.State.SUCCESS, updates.getLast().state());
        assertEquals(1, updates.getLast().successfulCards());
        assertTrue(terminal.card.disconnected);
    }

    @Test
    void stopAllowsInFlightWriteToFinishButPreventsNextWrite() throws Exception
    {
        final List<ReaderStatus> updates = new CopyOnWriteArrayList<>();
        final ReaderListener listener = new ReaderListener(updates::add);
        final FakeTerminal terminal = new FakeTerminal();
        terminal.card.channel.entered = new CountDownLatch(1);
        terminal.card.channel.release = new CountDownLatch(1);
        listener.activate(new NDefEncoder().encodeURI("https://example.com"));
        final Thread writer = Thread.ofPlatform().start(() -> listener.onCardInserted(terminal));
        try
        {
            assertTrue(terminal.card.channel.entered.await(2, TimeUnit.SECONDS));
            listener.deactivate();
            assertTrue(listener.isWriting());
            assertEquals(ReaderStatus.State.STOPPING, updates.getLast().state());
            assertThrows(IllegalStateException.class,
                    () -> listener.activate(new NDefEncoder().encodeURI("https://other.example")));
        }
        finally
        {
            terminal.card.channel.release.countDown();
            writer.join(2000);
        }
        assertFalse(writer.isAlive());
        assertFalse(listener.isWriting());
        assertEquals(ReaderStatus.State.STOPPED, updates.getLast().state());
        assertEquals(1, updates.getLast().successfulCards());
        listener.onCardInserted(terminal);
        assertEquals(1, terminal.connections);
    }

    private static final class FakeTerminal extends CardTerminal
    {
        private final FakeCard card = new FakeCard();
        private int connections;
        private boolean fail;
        public String getName() { return "Test reader"; }
        public Card connect(String protocol) throws CardException
        {
            connections++;
            if (fail) { throw new CardException("Card removed"); }
            return card;
        }
        public boolean isCardPresent() { return true; }
        public boolean waitForCardPresent(long timeout) { return true; }
        public boolean waitForCardAbsent(long timeout) { return false; }
    }

    private static final class FakeCard extends Card
    {
        private final FakeChannel channel = new FakeChannel();
        private boolean disconnected;
        public ATR getATR() { return new ATR(new byte[] {0x3B, 0}); }
        public String getProtocol() { return "T=1"; }
        public CardChannel getBasicChannel() { return channel; }
        public CardChannel openLogicalChannel() { throw new UnsupportedOperationException(); }
        public void beginExclusive() {}
        public void endExclusive() {}
        public byte[] transmitControlCommand(int code, byte[] data) { throw new UnsupportedOperationException(); }
        public void disconnect(boolean reset) { disconnected = true; }
    }

    private static final class FakeChannel extends CardChannel
    {
        private final byte[] memory = new byte[520];
        private CountDownLatch entered;
        private CountDownLatch release;
        public Card getCard() { throw new UnsupportedOperationException(); }
        public int getChannelNumber() { return 0; }
        public ResponseAPDU transmit(CommandAPDU command) throws CardException
        {
            if (entered != null)
            {
                entered.countDown();
                try
                {
                    if (!release.await(2, TimeUnit.SECONDS)) { throw new CardException("Timed out"); }
                }
                catch (InterruptedException exception)
                {
                    Thread.currentThread().interrupt();
                    throw new CardException(exception);
                }
            }
            final int offset = command.getP2() * 4;
            if (command.getINS() == 0xD6)
            {
                System.arraycopy(command.getData(), 0, memory, offset, 4);
                return new ResponseAPDU(new byte[] {(byte) 0x90, 0});
            }
            final byte[] response = Arrays.copyOfRange(memory, offset, offset + 6);
            response[4] = (byte) 0x90;
            response[5] = 0;
            return new ResponseAPDU(response);
        }
        public int transmit(ByteBuffer command, ByteBuffer response) { throw new UnsupportedOperationException(); }
        public void close() {}
    }
}
