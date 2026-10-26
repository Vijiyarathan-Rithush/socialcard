package com.rvi.application;


import com.rvi.domain.NDefMessage;
import com.rvi.service.IReaderService;
import com.rvi.service.ReaderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.Card;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import java.io.Reader;
import java.util.Objects;


public class ReaderListener implements IReaderListener
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ReaderListener.class);
    private final NDefMessage message;

    public ReaderListener(NDefMessage message)
    {
        this.message = Objects.requireNonNull(message, "NDEF message must not be null");
    }

    @Override
    public void onCardInserted(final CardTerminal terminal)
    {
        try
        {
            final Card card = terminal.connect("*");

            try
            {
                final IReaderRepository repository = new ReaderRepository(card.getBasicChannel());
                final IReaderService service = new ReaderService(repository);

                service.write(message);
                LOGGER.info("Card written and verified");
            }
            finally
            {
                card.disconnect(false);
            }
        }
        catch (CardException | RuntimeException ex)
        {
            onError(ex);
        }
    }

    @Override
    public void onCardRemoved(final CardTerminal terminal)
    {
        LOGGER.info("Card removed");
    }

    @Override
    public void onError(final Exception exception)
    {
        LOGGER.error("Reader operation failed", exception);
    }
}
