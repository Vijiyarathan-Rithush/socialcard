package com.rvi.service;

import com.rvi.application.IReaderRepository;
import com.rvi.application.NDefEncoder;
import com.rvi.application.ReaderRepository;
import com.rvi.application.exception.ReaderServiceException;
import com.rvi.domain.NDefMessage;

import javax.smartcardio.Card;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CardTerminals;
import javax.smartcardio.TerminalFactory;
import java.util.List;
import java.util.function.Supplier;

public final class ReaderService implements IReaderService
{
    private final Supplier<CardTerminals> terminalProvider;
    private final String readerName;
    private final NDefEncoder encoder = new NDefEncoder();
    private IReaderRepository repository;
    private Card card;

    public ReaderService()
    {
        this(() -> TerminalFactory.getDefault().terminals(), null);
    }

    public ReaderService(final IReaderRepository repository)
    {
        if (repository == null)
        {
            throw new ReaderServiceException("Repository is missing");
        }

        this.repository = repository;
        this.terminalProvider = null;
        this.readerName = null;
    }

    ReaderService(final Supplier<CardTerminals> terminalProvider, final String readerName)
    {
        if (terminalProvider == null)
        {
            throw new ReaderServiceException("Terminal provider is missing");
        }

        this.terminalProvider = terminalProvider;
        this.readerName = readerName;
    }

    /**
     * Lists exact PC/SC reader names without connecting to a card.
     * @return an immutable list, empty when no reader is connected
     * @throws ReaderServiceException if PC/SC discovery fails
     */
    public static List<String> listReaders()
    {
        return listReaders(() -> TerminalFactory.getDefault().terminals());
    }

    static List<String> listReaders(final Supplier<CardTerminals> provider)
    {
        try
        {
            return provider.get().list().stream().map(CardTerminal::getName).toList();
        }
        catch (CardException | IllegalStateException exception)
        {
            throw new ReaderServiceException("Could not list NFC readers; check the PC/SC service and reader driver", exception);
        }
    }

    /** Creates a disconnected service selecting the first available reader at connect time. */
    public static IReaderService create()
    {
        return new ReaderService();
    }

    /** Creates a disconnected service selecting the exact, nonblank PC/SC reader name at connect time. */
    public static IReaderService create(final String readerName)
    {
        if (readerName == null || readerName.isBlank())
        {
            throw new ReaderServiceException("Reader name is missing or blank");
        }

        return new ReaderService(() -> TerminalFactory.getDefault().terminals(), readerName);
    }

    @Override
    public synchronized void connect()
    {
        if (terminalProvider == null)
        {
            throw new ReaderServiceException("The supplied repository uses an externally managed connection");
        }

        if (card != null)
        {
            throw new ReaderServiceException("Service is already connected");
        }

        try
        {
            final CardTerminal terminal = findTerminal();

            if (!terminal.isCardPresent())
            {
                throw new ReaderServiceException("Place a card on reader " + terminal.getName() + " before calling connect()");
            }

            final Card connected = terminal.connect("*");

            try
            {
                final IReaderRepository connectedRepository = new ReaderRepository(connected.getBasicChannel());
                repository = connectedRepository;
                card = connected;
            }
            catch (RuntimeException exception)
            {
                try
                {
                    connected.disconnect(false);
                }
                catch (CardException | RuntimeException cleanupFailure)
                {
                    exception.addSuppressed(cleanupFailure);
                }

                throw new ReaderServiceException("Could not initialize the card connection", exception);
            }
        }
        catch (CardException | IllegalStateException exception)
        {
            throw new ReaderServiceException("Could not connect to the NFC card", exception);
        }
    }

    @Override
    public synchronized void disconnect()
    {
        if (card == null)
        {
            return;
        }

        try
        {
            card.disconnect(false);
        }
        catch (CardException | IllegalStateException exception)
        {
            throw new ReaderServiceException("Could not disconnect from the NFC card", exception);
        }
        finally
        {
            card = null;
            repository = null;
        }
    }

    @Override
    public NDefMessage encodeURI(final String message)
    {
        return encoder.encodeURI(message);
    }

    @Override
    public NDefMessage encodeURIs(final List<String> urls)
    {
        return encoder.encodeURIs(urls);
    }

    @Override
    public synchronized NDefMessage read()
    {
        requireRepository();

        try
        {
            return repository.read();
        }
        catch (CardException exception)
        {
            throw new ReaderServiceException("Could not read the NFC card", exception);
        }
    }

    @Override
    public synchronized void write(final NDefMessage message)
    {
        requireRepository();

        try
        {
            repository.write(message);
        }
        catch (CardException exception)
        {
            throw new ReaderServiceException("Could not write the NFC card", exception);
        }
    }

    @Override
    public void close()
    {
        disconnect();
    }

    private CardTerminal findTerminal() throws CardException
    {
        final List<CardTerminal> terminals = terminalProvider.get().list();

        if (terminals.isEmpty())
        {
            throw new ReaderServiceException("No NFC reader was found; connect a PC/SC reader and check its driver and PC/SC service");
        }

        if (readerName == null)
        {
            return terminals.getFirst();
        }

        for (final CardTerminal terminal : terminals)
        {
            if (readerName.equals(terminal.getName()))
            {
                return terminal;
            }
        }

        throw new ReaderServiceException("NFC reader was not found: " + readerName);
    }

    private void requireRepository()
    {
        if (repository == null)
        {
            throw new ReaderServiceException("Service is not connected; call connect() first");
        }
    }
}
