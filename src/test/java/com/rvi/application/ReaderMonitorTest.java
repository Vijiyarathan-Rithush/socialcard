package com.rvi.application;

import org.junit.jupiter.api.Test;
import javax.smartcardio.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ReaderMonitorTest
{
    @Test
    void closingSuppressesShutdownFailureAndPreventsStartingAgain() throws Exception
    {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch exited = new CountDownLatch(1);
        final AtomicReference<Exception> received = new AtomicReference<>();
        final CardTerminal terminal = new CardTerminal()
        {
            public String getName() { return "test"; }
            public Card connect(String protocol) { throw new UnsupportedOperationException(); }
            public boolean isCardPresent() { return false; }
            public boolean waitForCardPresent(long timeout) throws CardException
            {
                entered.countDown();
                try { release.await(3, TimeUnit.SECONDS); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                finally { exited.countDown(); }
                throw new CardException("Closing");
            }
            public boolean waitForCardAbsent(long timeout) { return true; }
        };
        final ReaderMonitor monitor = new ReaderMonitor(terminal, new IReaderListener()
        {
            public void onCardInserted(CardTerminal value) { fail("Unexpected insertion"); }
            public void onCardRemoved(CardTerminal value) { fail("Unexpected removal"); }
            public void onError(Exception exception) { received.set(exception); }
        });
        try
        {
            monitor.start();
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            monitor.close();
            release.countDown();
            assertTrue(exited.await(3, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, monitor::start);
            assertNull(received.get());
        }
        finally { monitor.close(); release.countDown(); }
        final ReaderMonitor neverStarted = new ReaderMonitor(terminal, new ReaderListener(new NDefEncoder().encodeURI("https://example.com")));
        neverStarted.close();
        assertThrows(IllegalStateException.class, neverStarted::start);
    }

    @Test
    void forwardsPollingFailureAndStops() throws Exception
    {
        final CardException failure = new CardException("Reader unplugged");
        final CountDownLatch reported = new CountDownLatch(1);
        final AtomicReference<Exception> received = new AtomicReference<>();
        final CardTerminal terminal = new CardTerminal()
        {
            public String getName() { return "test"; }
            public Card connect(String protocol) { throw new UnsupportedOperationException(); }
            public boolean isCardPresent() { return false; }
            public boolean waitForCardPresent(long timeout) throws CardException { throw failure; }
            public boolean waitForCardAbsent(long timeout) { return true; }
        };
        try (final ReaderMonitor monitor = new ReaderMonitor(terminal, new IReaderListener()
        {
            public void onCardInserted(CardTerminal value) { fail("Unexpected insertion"); }
            public void onCardRemoved(CardTerminal value) { fail("Unexpected removal"); }
            public void onError(Exception exception) { received.set(exception); reported.countDown(); }
        }))
        {
            monitor.start();
            assertTrue(reported.await(3, TimeUnit.SECONDS));
            assertSame(failure, received.get());
            assertThrows(IllegalStateException.class, monitor::start);
        }
    }
}
