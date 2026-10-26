package com.rvi.application;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;

public final class ReaderMonitor implements AutoCloseable
{
    private static final int CARD_POLL_TIMEOUT_MILLIS = 500;

    private final CardTerminal terminal;
    private final IReaderListener listener;

    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private boolean closed;

    public ReaderMonitor(final CardTerminal terminal, final IReaderListener listener)
    {
        this.terminal = Objects.requireNonNull(terminal);
        this.listener = Objects.requireNonNull(listener);
    }

    public synchronized void start()
    {
        if (closed)
        {
            throw new IllegalStateException("Monitor is closed");
        }
        if (!started.compareAndSet(false, true))
        {
            throw new IllegalStateException("Monitor already started");
        }
        running.set(true);
        executor.submit(this::monitor);
    }

    private void monitor()
    {
        try
        {
            while (running.get())
            {
                if (!terminal.waitForCardPresent(CARD_POLL_TIMEOUT_MILLIS))
                {
                    continue;
                }
                if (!running.get())
                {
                    return;
                }

                listener.onCardInserted(terminal);

                while (running.get())
                {
                    if (terminal.waitForCardAbsent(CARD_POLL_TIMEOUT_MILLIS))
                    {
                        if (running.get())
                        {
                            listener.onCardRemoved(terminal);
                        }
                        break;
                    }
                }
            }
        }
        catch (CardException | RuntimeException exception)
        {
            if (running.get())
            {
                listener.onError(exception);
            }
        }
        finally
        {
            running.set(false);
            executor.shutdown();
        }
    }

    public void awaitStopped() throws InterruptedException
    {
        while (!executor.awaitTermination(1, TimeUnit.SECONDS))
        {
            // Finish an in-flight card operation before releasing the application.
        }
    }

    @Override
    public synchronized void close()
    {
        closed = true;
        running.set(false);
        executor.shutdown();
    }
}
