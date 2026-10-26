package com.rvi.application;

import com.rvi.domain.NDefMessage;
import com.rvi.service.IReaderService;
import com.rvi.service.ReaderService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import javax.smartcardio.Card;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;

public final class ReaderListener implements IReaderListener
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ReaderListener.class);
    private final IReaderStatusListener statusListener;
    private NDefMessage message;
    private boolean writing;
    private int successfulCards;

    public ReaderListener(final IReaderStatusListener statusListener)
    {
        this.statusListener = Objects.requireNonNull(statusListener);
    }

    // Preserve the original programmatic use without a TUI.
    public ReaderListener(final NDefMessage message)
    {
        this(status -> LOGGER.info("{}", status.message()));
        this.message = Objects.requireNonNull(message);
    }

    public synchronized void activate(final NDefMessage message)
    {
        if (writing)
        {
            throw new IllegalStateException("A card is still being written");
        }
        this.message = Objects.requireNonNull(message);
        publish(ReaderStatus.State.ACTIVE, "Waiting for a card. Batch mode active.");
    }

    public synchronized void deactivate()
    {
        message = null;
        publish(
                writing ? ReaderStatus.State.STOPPING : ReaderStatus.State.STOPPED,
                writing ? "Stopping after the current write ..." : "Batch stopped.");
    }

    public synchronized boolean isWriting()
    {
        return writing;
    }

    @Override
    public void onCardInserted(final CardTerminal terminal)
    {
        final NDefMessage currentMessage;
        synchronized (this)
        {
            if (message == null || writing)
            {
                return;
            }
            currentMessage = message;
            writing = true;
            publish(ReaderStatus.State.WRITING, "Card detected. Writing and verifying ...");
        }

        Exception failure = null;
        boolean verified = false;
        try
        {
            final Card card = terminal.connect("*");
            try
            {
                final IReaderService service =
                        new ReaderService(new ReaderRepository(card.getBasicChannel()));
                service.write(currentMessage);
                verified = true;
            }
            finally
            {
                card.disconnect(false);
            }
        }
        catch (CardException | RuntimeException exception)
        {
            failure = exception;
            LOGGER.error("Reader operation failed", exception);
        }

        synchronized (this)
        {
            writing = false;
            if (verified)
            {
                successfulCards++;
            }
            if (failure != null)
            {
                publish(
                        ReaderStatus.State.ERROR,
                        "Card error: " + failure.getMessage() + ". Remove the card and try again.");
            }
            else
            {
                publish(ReaderStatus.State.SUCCESS, "Card written and verified. Remove the card.");
            }
            if (message == null)
            {
                publish(ReaderStatus.State.STOPPED, "Batch stopped.");
            }
        }
    }

    @Override
    public synchronized void onCardRemoved(final CardTerminal terminal)
    {
        if (message != null)
        {
            publish(ReaderStatus.State.ACTIVE, "Card removed. Waiting for the next card.");
        }
    }

    @Override
    public synchronized void onError(final Exception exception)
    {
        message = null;
        LOGGER.error("Reader monitoring failed", exception);
        publish(ReaderStatus.State.ERROR, "Reader error: " + exception.getMessage());
        publish(
                ReaderStatus.State.STOPPED,
                "Monitoring stopped. Check the reader and restart the batch.");
    }

    private void publish(final ReaderStatus.State state, final String text)
    {
        statusListener.onStatusChanged(new ReaderStatus(state, text, successfulCards));
    }
}
