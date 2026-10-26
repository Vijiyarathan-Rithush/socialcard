package com.rvi.application;

import org.junit.jupiter.api.Test;
import javax.smartcardio.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ReaderMonitorTest
{
    @Test
    void reportsMonitoringFailureAndTerminates() throws Exception
    {
        final CountDownLatch error = new CountDownLatch(1);
        final FakeTerminal terminal = new FakeTerminal();
        terminal.fail = true;
        try (ReaderMonitor monitor = new ReaderMonitor(terminal, new IReaderListener()
        {
            public void onCardInserted(CardTerminal terminal) { fail("Unexpected card"); }
            public void onCardRemoved(CardTerminal terminal) { fail("Unexpected removal"); }
            public void onError(Exception exception) { error.countDown(); }
        }))
        {
            monitor.start();
            assertTrue(error.await(2, TimeUnit.SECONDS));
            monitor.awaitStopped();
        }
    }

    @Test
    void waitsForRemovalBeforeDetectingAnotherCard() throws Exception
    {
        final FakeTerminal terminal = new FakeTerminal();
        final AtomicInteger inserted = new AtomicInteger();
        final CountDownLatch removed = new CountDownLatch(1);
        try (ReaderMonitor monitor = new ReaderMonitor(terminal, new IReaderListener()
        {
            public void onCardInserted(CardTerminal terminal) { inserted.incrementAndGet(); }
            public void onCardRemoved(CardTerminal terminal) { removed.countDown(); }
            public void onError(Exception exception) { fail(exception); }
        }))
        {
            monitor.start();
            try
            {
                assertTrue(terminal.waitingForRemoval.await(2, TimeUnit.SECONDS));
                assertEquals(1, inserted.get());
                terminal.remove.countDown();
                assertTrue(removed.await(2, TimeUnit.SECONDS));
            }
            finally
            {
                monitor.close();
                terminal.remove.countDown();
                monitor.awaitStopped();
            }
        }
    }

    @Test
    void cannotStartAClosedMonitor() throws Exception
    {
        final ReaderMonitor monitor = new ReaderMonitor(new FakeTerminal(), new IReaderListener()
        {
            public void onCardInserted(CardTerminal terminal) {}
            public void onCardRemoved(CardTerminal terminal) {}
            public void onError(Exception exception) {}
        });
        monitor.close();
        assertThrows(IllegalStateException.class, monitor::start);
        monitor.awaitStopped();
    }

    private static final class FakeTerminal extends CardTerminal
    {
        private final CountDownLatch waitingForRemoval = new CountDownLatch(1);
        private final CountDownLatch remove = new CountDownLatch(1);
        private boolean fail;
        private boolean presented;
        public String getName() { return "Test reader"; }
        public Card connect(String protocol) { throw new UnsupportedOperationException(); }
        public boolean isCardPresent() { return true; }
        public boolean waitForCardPresent(long timeout) throws CardException
        {
            if (fail) { throw new CardException("Reader disconnected"); }
            if (!presented) { presented = true; return true; }
            try { new CountDownLatch(1).await(timeout, TimeUnit.MILLISECONDS); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            return false;
        }
        public boolean waitForCardAbsent(long timeout) throws CardException
        {
            waitingForRemoval.countDown();
            try { return remove.await(timeout, TimeUnit.MILLISECONDS); }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
                throw new CardException(exception);
            }
        }
    }
}
