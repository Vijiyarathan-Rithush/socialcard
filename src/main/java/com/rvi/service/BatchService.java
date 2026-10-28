package com.rvi.service;

import com.rvi.application.NDefEncoder;
import com.rvi.application.ReaderListener;
import com.rvi.application.ReaderMonitor;
import com.rvi.application.ReaderStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.TerminalFactory;

public final class BatchService implements IBatchService
{
    private final List<ReaderMonitor> monitors = new ArrayList<>();
    private ReaderListener listener;
    private boolean active;
    private boolean closed;

    @Override
    public List<String> readers() throws CardException
    {
        return TerminalFactory.getDefault().terminals().list().stream()
                .map(CardTerminal::getName).toList();
    }

    @Override
    public synchronized void start(final String reader, final String url,
                                   final Consumer<BatchStatus> onStatus)
    {
        if (closed || active)
        {
            throw new IllegalStateException("A batch is already active or the service is closed");
        }
        final var message = new NDefEncoder().encodeURI(url);
        try
        {
            final var terminal = TerminalFactory.getDefault().terminals().getTerminal(reader);
            if (terminal == null)
            {
                throw new IllegalArgumentException("Select a connected reader");
            }
            listener = new ReaderListener(status -> {
                final boolean stopped = status.state() == ReaderStatus.State.STOPPED;
                synchronized (BatchService.this)
                {
                    if (stopped)
                    {
                        active = false;
                    }
                }
                onStatus.accept(new BatchStatus(status.message(), status.successfulCards(), stopped));
            });
            monitors.forEach(ReaderMonitor::close);
            final var monitor = new ReaderMonitor(terminal, listener);
            monitors.add(monitor);
            active = true;
            listener.activate(message);
            monitor.start();
        }
        catch (RuntimeException exception)
        {
            active = false;
            throw exception;
        }
    }

    @Override
    public void stop()
    {
        final ReaderListener current;
        synchronized (this)
        {
            current = listener;
            monitors.forEach(ReaderMonitor::close);
        }
        if (current != null)
        {
            current.deactivate();
        }
    }

    @Override
    public void close()
    {
        synchronized (this)
        {
            closed = true;
        }
        stop();
        for (final var monitor : monitors)
        {
            try
            {
                monitor.awaitStopped();
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
