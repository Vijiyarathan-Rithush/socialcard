package com.rvi.application;

import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ReaderMonitor implements AutoCloseable
{
    private final CardTerminal terminal;
    private final IReaderListener listener;

    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean running = new AtomicBoolean();
    private static final int TIMEOUT = 500;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public ReaderMonitor(final CardTerminal terminal, final IReaderListener listener)
    {
        this.terminal = Objects.requireNonNull(terminal);
        this.listener = Objects.requireNonNull(listener);
    }

    public void start()
    {
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
                if (!terminal.waitForCardPresent(TIMEOUT))
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
                    if (terminal.waitForCardAbsent(TIMEOUT))
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
        catch (CardException | RuntimeException ex)
        {
            if (!running.get())
            {
                listener.onError(ex);
            }
        }
        finally
        {
            running.set(false);
        }
    }

    @Override
    public void close() throws Exception
    {
        running.set(false);
        executor.shutdown();
    }
}
