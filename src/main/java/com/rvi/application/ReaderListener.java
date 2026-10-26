package com.rvi.application;

import com.rvi.domain.NDefMessage;
import com.rvi.service.IReaderService;
import com.rvi.service.ReaderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.Card;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import java.util.Objects;

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
        publish(ReaderStatus.State.ACTIVE, "Warte auf Karte. Serienmodus aktiv.");
    }

    public synchronized void deactivate()
    {
        message = null;
        publish(writing ? ReaderStatus.State.STOPPING : ReaderStatus.State.STOPPED,
                writing ? "Stoppe nach dem laufenden Schreibvorgang ..." : "Serienmodus gestoppt.");
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
            publish(ReaderStatus.State.WRITING, "Karte erkannt. Schreibe und verifiziere ...");
        }

        Exception failure = null;
        boolean verified = false;
        try
        {
            final Card card = terminal.connect("*");
            try
            {
                final IReaderService service = new ReaderService(
                        new ReaderRepository(card.getBasicChannel()));
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
                publish(ReaderStatus.State.ERROR, "Kartenfehler: " + failure.getMessage()
                        + ". Karte entfernen und erneut auflegen.");
            }
            else
            {
                publish(ReaderStatus.State.SUCCESS, "Karte beschrieben und verifiziert. Karte entfernen.");
            }
            if (message == null)
            {
                publish(ReaderStatus.State.STOPPED, "Serienmodus gestoppt.");
            }
        }
    }

    @Override
    public synchronized void onCardRemoved(final CardTerminal terminal)
    {
        if (message != null)
        {
            publish(ReaderStatus.State.ACTIVE, "Karte entfernt. Warte auf die nächste Karte.");
        }
    }

    @Override
    public synchronized void onError(final Exception exception)
    {
        message = null;
        LOGGER.error("Reader monitoring failed", exception);
        publish(ReaderStatus.State.ERROR, "Readerfehler: " + exception.getMessage());
        publish(ReaderStatus.State.STOPPED, "Überwachung beendet. Reader prüfen und erneut aktivieren.");
    }

    private void publish(final ReaderStatus.State state, final String text)
    {
        statusListener.onStatusChanged(new ReaderStatus(state, text, successfulCards));
    }
}
