package com.rvi.infrastructure;

import com.rvi.application.IReaderRepository;
import com.rvi.domain.ICardStatus;
import com.rvi.domain.NdefMessage;
import com.rvi.domain.Type2Tlv;
import com.rvi.exception.ReaderException;
import java.util.List;
import java.util.Objects;
import javax.smartcardio.Card;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.TerminalFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.rvi.exception.ReaderException.Reason.*;
import static com.rvi.infrastructure.Ntag215Layout.*;

public final class PcscReaderRepository implements IReaderRepository
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PcscReaderRepository.class);
    private final ITerminalProvider terminals;

    public PcscReaderRepository()
    {
        this(() -> TerminalFactory.getDefault().terminals().list());
    }

    public PcscReaderRepository(final ITerminalProvider terminals)
    {
        this.terminals = Objects.requireNonNull(terminals);
    }

    @Override
    public List<String> readers() throws ReaderException
    {
        try
        {
            return terminals.terminals().stream().map(CardTerminal::getName).toList();
        }
        catch (CardException exception)
        {
            throw new ReaderException(IO, "Available readers could not be listed.", exception);
        }
    }

    @Override
    public ICardStatus status(final String reader) throws ReaderException
    {
        try
        {
            return withCard(reader, repository ->
            {
                final boolean capabilityWritable = repository.inspectCapability();
                boolean writable = false;
                if (capabilityWritable)
                {
                    try
                    {
                        writable = repository.isWritable();
                    }
                    catch (ReaderException exception)
                    {
                        LOGGER.debug("Protection details are unavailable; writing is disabled for reader {}",
                                reader, exception);
                    }
                }
                return new ICardStatus.Ready(writable, DATA_BYTES);
            });
        }
        catch (ReaderException exception)
        {
            return switch (exception.reason())
            {
                case MISSING_CARD -> new ICardStatus.NoCard();
                case UNSUPPORTED -> new ICardStatus.Unsupported();
                default -> throw exception;
            };
        }
    }

    @Override
    public NdefMessage read(final String reader) throws ReaderException
    {
        return withCard(reader, repository ->
        {
            repository.inspectCapability();
            return repository.read();
        });
    }

    @Override
    public void write(final String reader, final NdefMessage message) throws ReaderException
    {
        Objects.requireNonNull(message);
        if (message.size() > DATA_BYTES || Type2Tlv.paddedEncodedSize(message.size()) > DATA_BYTES)
        {
            throw new ReaderException(CAPACITY_EXCEEDED, "The NDEF message does not fit the tag data area.");
        }
        withCard(reader, repository ->
        {
            if (!repository.inspectCapability())
            {
                throw new ReaderException(ACCESS_DENIED, "The capability container marks this tag as read-only.");
            }
            repository.write(message);
            LOGGER.info("Verified NDEF write of {} bytes to reader {}", message.size(), reader);
            return null;
        });
    }

    private <T> T withCard(final String reader, final ICardOperation<T> operation) throws ReaderException
    {
        Card card = null;
        boolean exclusive = false;
        Throwable primaryFailure = null;
        try
        {
            final CardTerminal terminal = terminals.terminals().stream()
                    .filter(candidate -> candidate.getName().equals(reader))
                    .findFirst()
                    .orElseThrow(() -> new ReaderException(MISSING_READER, "The selected reader is unavailable."));
            if (!terminal.isCardPresent())
            {
                throw new ReaderException(MISSING_CARD, "Place a tag on the reader.");
            }
            card = terminal.connect("*");
            card.beginExclusive();
            exclusive = true;
            return operation.apply(new Ntag215Memory(card.getBasicChannel()));
        }
        catch (CardException exception)
        {
            final ReaderException failure = new ReaderException(IO, "A reader session could not be opened.", exception);
            primaryFailure = failure;
            throw failure;
        }
        catch (ReaderException | RuntimeException | Error exception)
        {
            primaryFailure = exception;
            throw exception;
        }
        finally
        {
            if (card != null)
            {
                if (exclusive)
                {
                    try
                    {
                        card.endExclusive();
                    }
                    catch (CardException | RuntimeException exception)
                    {
                        cleanupFailure(primaryFailure, exception, "release exclusive access", reader);
                    }
                }
                try
                {
                    card.disconnect(false);
                }
                catch (CardException | RuntimeException exception)
                {
                    cleanupFailure(primaryFailure, exception, "disconnect the card", reader);
                }
            }
        }
    }

    private static void cleanupFailure(final Throwable primary, final Exception cleanup,
                                       final String action, final String reader)
    {
        if (primary != null)
        {
            primary.addSuppressed(cleanup);
        }
        else
        {
            // A completed and verified write must remain a success if only cleanup fails.
            LOGGER.warn("Could not {} for reader {} after the operation completed", action, reader, cleanup);
        }
    }

    @FunctionalInterface
    private interface ICardOperation<T>
    {
        T apply(Ntag215Memory repository) throws ReaderException;
    }
}
