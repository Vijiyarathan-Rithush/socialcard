package com.rvi.application;

import com.rvi.domain.NDefMessage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Objects;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import static com.rvi.domain.NdefFormat.*;

public final class ReaderRepository implements IReaderRepository
{
    private static final int PAGE_BYTES = 4;
    private static final int FIRST_PAGE = 4;
    private static final int LAST_PAGE = 129;
    private static final int CAPACITY = (LAST_PAGE - FIRST_PAGE + 1) * PAGE_BYTES;
    private static final int CLA = 0xFF;
    private static final int READ = 0xB0;
    private static final int WRITE = 0xD6;
    private static final int P1 = 0x00;
    private static final int SUCCESS = 0x9000;
    private final CardChannel channel;

    public ReaderRepository(final CardChannel channel)
    {
        this.channel = Objects.requireNonNull(channel);
    }

    @Override
    public NDefMessage read() throws CardException
    {
        final byte[] first = readPage(FIRST_PAGE);
        final int length = Byte.toUnsignedInt(first[1]);

        if (Byte.toUnsignedInt(first[0]) != TLV_TYPE || length == 0 || length == EXTENDED_LENGTH)
        {
            throw new CardException("Karte leer oder NDEF-Format nicht unterstützt.");
        }

        final int total = length + TLV_OVERHEAD;
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(first);

        for (int page = FIRST_PAGE + 1; output.size() < total; page++)
        {
            output.writeBytes(readPage(page));
        }

        return new NDefMessage(Arrays.copyOf(output.toByteArray(), total));
    }

    @Override
    public void write(final NDefMessage message) throws CardException
    {
        final byte[] data = Objects.requireNonNull(message).data();
        final int paddedLength = ((data.length + PAGE_BYTES - 1) / PAGE_BYTES) * PAGE_BYTES;

        if (paddedLength > CAPACITY)
        {
            throw new IllegalArgumentException("Nachricht überschreitet den Kartenspeicher.");
        }

        final byte[] padded = Arrays.copyOf(data, paddedLength);
        writePage(FIRST_PAGE, new byte[] {(byte) TLV_TYPE, 0, (byte) TERMINATOR, 0});

        for (int offset = PAGE_BYTES; offset < padded.length; offset += PAGE_BYTES)
        {
            writePage(FIRST_PAGE + offset / PAGE_BYTES, Arrays.copyOfRange(padded, offset, offset + PAGE_BYTES));
        }

        writePage(FIRST_PAGE, Arrays.copyOfRange(padded, 0, PAGE_BYTES));

        if (!Arrays.equals(data, read().data()))
        {
            throw new CardException("Überprüfung nach dem Schreiben fehlgeschlagen.");
        }
    }

    private byte[] readPage(final int page) throws CardException
    {
        validatePage(page);
        final ResponseAPDU response = channel.transmit(new CommandAPDU(CLA, READ, P1, page, PAGE_BYTES));
        check(response, page);
        final byte[] data = response.getData();

        if (data.length != PAGE_BYTES)
        {
            throw new CardException("Erwartet: " + PAGE_BYTES + " Bytes.");
        }

        return data;
    }

    private void writePage(final int page, final byte[] data) throws CardException
    {
        validatePage(page);

        if (data == null || data.length != PAGE_BYTES)
        {
            throw new IllegalArgumentException("Erwartet: " + PAGE_BYTES + " Bytes.");
        }

        check(channel.transmit(new CommandAPDU(CLA, WRITE, P1, page, data)), page);
    }

    private void check(final ResponseAPDU response, final int page) throws CardException
    {
        if (response.getSW() != SUCCESS)
        {
            throw new CardException(String.format("Zugriff auf Page %d fehlgeschlagen: %04X", page, response.getSW()));
        }
    }

    private void validatePage(final int page)
    {
        if (page < FIRST_PAGE || page > LAST_PAGE)
        {
            throw new IllegalArgumentException("Page ausserhalb des Benutzerspeichers.");
        }
    }
}
