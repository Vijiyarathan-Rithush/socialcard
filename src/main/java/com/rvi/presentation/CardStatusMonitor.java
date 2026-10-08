package com.rvi.presentation;

import com.rvi.domain.ICardStatus;
import com.rvi.service.IReaderService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Polls one selected reader and discards results invalidated by selection or user actions. */
final class CardStatusMonitor implements AutoCloseable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(CardStatusMonitor.class);
    private static final Duration POLL_INTERVAL = Duration.seconds(2);
    private final IReaderService service;
    private final ReaderTaskExecutor tasks;
    private final Supplier<String> selectedReader;
    private final Consumer<ICardStatus> statusChanged;
    private final Timeline poller;
    private long revision;
    private boolean inspecting;
    private boolean closed;
    private String lastFailure;

    CardStatusMonitor(final IReaderService service, final ReaderTaskExecutor tasks,
                final Supplier<String> selectedReader, final Consumer<ICardStatus> statusChanged)
    {
        this.service = service;
        this.tasks = tasks;
        this.selectedReader = selectedReader;
        this.statusChanged = statusChanged;
        poller = new Timeline(new KeyFrame(POLL_INTERVAL, event -> inspect()));
        poller.setCycleCount(Timeline.INDEFINITE);
    }

    void start()
    {
        poller.play();
    }

    void invalidate()
    {
        revision++;
    }

    void inspect()
    {
        final String reader = selectedReader.get();
        if (closed || inspecting || reader == null || tasks.busy() || tasks.stopping())
        {
            return;
        }
        final long expectedRevision = revision;
        inspecting = true;
        if (!tasks.inspect(() -> service.status(reader), status ->
        {
            inspecting = false;
            if (accept(reader, expectedRevision))
            {
                lastFailure = null;
                statusChanged.accept(status);
            }
        }, failure ->
        {
            inspecting = false;
            if (accept(reader, expectedRevision))
            {
                final String signature = reader + ":" + failure.getClass().getName() + ":" + failure.getMessage();
                if (!signature.equals(lastFailure))
                {
                    LOGGER.warn("Card inspection failed for reader '{}'", reader, failure);
                    lastFailure = signature;
                }
                statusChanged.accept(new ICardStatus.Unavailable());
            }
        }))
        {
            inspecting = false;
        }
    }

    private boolean accept(final String reader, final long expectedRevision)
    {
        return !closed && !tasks.busy() && !tasks.stopping()
                && revision == expectedRevision && Objects.equals(reader, selectedReader.get());
    }

    @Override
    public void close()
    {
        closed = true;
        invalidate();
        poller.stop();
    }
}
