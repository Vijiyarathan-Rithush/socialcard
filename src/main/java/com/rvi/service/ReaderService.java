package com.rvi.service;

import com.rvi.application.ReaderRepository;
import com.rvi.domain.CardStatus;
import com.rvi.domain.NDefMessage;
import javax.smartcardio.*;
import java.util.List;

public final class ReaderService
{
    private static final int CLA = 0xFF;
    private static final int READ_BINARY = 0xB0;
    private static final int CC_PAGE = 3;
    private static final int PAGE_BYTES = 4;
    private static final int CC_MAGIC = 0xE1;
    private static final int SIZE_UNIT = 8;
    private static final int NTAG215_CC_BYTES = 496;
    private static final int SUCCESS = 0x9000;
    private final NdefUriEncoder encoder = new NdefUriEncoder();

    public List<String> readers() throws CardException
    {
        return TerminalFactory.getDefault().terminals().list().stream().map(CardTerminal::getName).toList();
    }

    public CardStatus status(final String reader) throws CardException
    {
        final CardTerminal terminal = terminal(reader);

        if (!terminal.isCardPresent())
        {
            return new CardStatus(false, false, false, "Keine Karte", 0);
        }

        final Card card = terminal.connect("*");

        try
        {
            return inspect(card);
        }
        finally
        {
            card.disconnect(false);
        }
    }

    public NDefMessage encodeURIs(final List<String> urls)
    {
        return encoder.encodeURIs(urls);
    }

    public List<String> read(final String reader) throws CardException
    {
        final Card card = connect(reader);

        try
        {
            requireSupported(inspect(card));
            return encoder.decode(new ReaderRepository(card.getBasicChannel()).read());
        }
        finally
        {
            card.disconnect(false);
        }
    }

    public void write(final String reader, final List<String> urls) throws CardException
    {
        final NDefMessage message = encoder.encodeURIs(urls);
        final Card card = connect(reader);

        try
        {
            final CardStatus status = inspect(card);
            requireSupported(status);

            if (!status.writable())
            {
                throw new CardException("Die Karte erlaubt laut Capability Container keinen freien Schreibzugriff.");
            }

            if (message.data().length > status.capacity())
            {
                throw new CardException("Die Nachricht überschreitet den verfügbaren NDEF-Speicher.");
            }

            new ReaderRepository(card.getBasicChannel()).write(message);
        }
        finally
        {
            card.disconnect(false);
        }
    }

    private CardStatus inspect(final Card card) throws CardException
    {
        final ResponseAPDU response = card.getBasicChannel().transmit(new CommandAPDU(CLA, READ_BINARY, 0, CC_PAGE, PAGE_BYTES));
        final byte[] cc = response.getData();

        if (response.getSW() != SUCCESS || cc.length != PAGE_BYTES)
        {
            return new CardStatus(true, false, false, "Nicht unterstützt", 0);
        }

        if (Byte.toUnsignedInt(cc[0]) != CC_MAGIC || Byte.toUnsignedInt(cc[2]) * SIZE_UNIT != NTAG215_CC_BYTES || (cc[1] & 0xF0) != 0x10 || (cc[3] & 0xF0) != 0)
        {
            return new CardStatus(true, false, false, "Nicht unterstützt", 0);
        }

        return new CardStatus(true, true, (cc[3] & 0x0F) == 0, "NTAG215-kompatibel", NTAG215_CC_BYTES);
    }

    private void requireSupported(final CardStatus status) throws CardException
    {
        if (!status.supported())
        {
            throw new CardException("Unterstützt wird das NTAG215-Type-2-Layout. MIFARE Classic und DESFire sind nicht implementiert.");
        }
    }

    private Card connect(final String reader) throws CardException
    {
        final CardTerminal terminal = terminal(reader);

        if (!terminal.isCardPresent())
        {
            throw new CardException("Bitte eine Karte auf den Reader legen.");
        }

        return terminal.connect("*");
    }

    private CardTerminal terminal(final String name) throws CardException
    {
        for (final CardTerminal terminal : TerminalFactory.getDefault().terminals().list())
        {
            if (terminal.getName().equals(name))
            {
                return terminal;
            }
        }

        throw new CardException("Reader nicht verfügbar. Bitte aktualisieren.");
    }
}
