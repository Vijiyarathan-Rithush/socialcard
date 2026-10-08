package com.rvi.infrastructure;

import com.rvi.domain.NdefMessage;
import com.rvi.domain.Type2Tlv;
import com.rvi.exception.InvalidNdefException;
import com.rvi.exception.ReaderException;
import java.util.Arrays;
import java.util.Objects;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import static com.rvi.exception.ReaderException.Reason.*;
import static com.rvi.infrastructure.Ntag215Layout.*;

/** Page I/O for the supported NTAG215 layout. No method writes lock/configuration pages. */
final class Ntag215Memory
{
    private static final int CLA = 0xFF;
    private static final int READ_BINARY = 0xB0;
    private static final int UPDATE_BINARY = 0xD6;
    private static final int SUCCESS = 0x9000;
    private static final byte[] EMPTY_TLV_PAGE = {0x03, 0, (byte) 0xFE, 0};
    private final CardChannel channel;

    Ntag215Memory(final CardChannel channel)
    {
        this.channel = Objects.requireNonNull(channel);
    }

    boolean inspectCapability() throws ReaderException
    {
        final byte[] cc = readPage(CC_PAGE);
        if (Byte.toUnsignedInt(cc[0]) != CC_MAGIC
                || (cc[1] & HIGH_NIBBLE_MASK) != CC_VERSION_MAJOR
                || Byte.toUnsignedInt(cc[2]) * CC_SIZE_UNIT != DATA_BYTES
                || (cc[3] & HIGH_NIBBLE_MASK) != 0)
        {
            throw new ReaderException(UNSUPPORTED, "Only the NTAG215 Type 2 memory layout is supported.");
        }
        return (cc[3] & NIBBLE_MASK) == 0;
    }

    boolean isWritable() throws ReaderException
    {
        return protection().allows(FIRST_DATA_PAGE, LAST_DATA_PAGE);
    }

    NdefMessage read() throws ReaderException
    {
        return content().message();
    }

    void write(final NdefMessage message) throws ReaderException
    {
        Objects.requireNonNull(message);
        if (message.size() > DATA_BYTES || Type2Tlv.paddedEncodedSize(message.size()) > DATA_BYTES)
        {
            throw new ReaderException(CAPACITY_EXCEEDED, "The NDEF message does not fit the tag data area.");
        }
        final byte[] encoded = Type2Tlv.encode(message);
        final int paddedLength = paddedSize(encoded.length);
        final int lastPage = FIRST_DATA_PAGE + paddedLength / PAGE_BYTES - 1;
        if (!protection().allows(FIRST_DATA_PAGE, lastPage))
        {
            throw new ReaderException(ACCESS_DENIED, "The target pages are locked, password protected, or mirrored.");
        }
        // Never discard unknown layout metadata or write through reserved bytes.
        if (content().hasAdditionalTlvs())
        {
            throw new ReaderException(UNSUPPORTED,
                    "Writing tags with control or proprietary TLVs is not supported; the tag was not changed.");
        }

        final byte[] padded = Arrays.copyOf(encoded, paddedLength);
        try
        {
            writePage(FIRST_DATA_PAGE, EMPTY_TLV_PAGE);
            for (int offset = PAGE_BYTES; offset < paddedLength; offset += PAGE_BYTES)
            {
                writePage(FIRST_DATA_PAGE + offset / PAGE_BYTES,
                        Arrays.copyOfRange(padded, offset, offset + PAGE_BYTES));
            }
            // Commit the length only after every payload page has been acknowledged.
            writePage(FIRST_DATA_PAGE, Arrays.copyOf(padded, PAGE_BYTES));
            if (!message.equals(read()))
            {
                throw new ReaderException(VERIFICATION_FAILED, "The tag contents differ from the written NDEF message.");
            }
        }
        catch (ReaderException exception)
        {
            // Once a write has been sent, power loss or a lost acknowledgement makes rollback unsafe.
            throw new ReaderException(exception.reason(),
                    "The write could not be verified; the tag contents may be incomplete. " + exception.getMessage(),
                    exception);
        }
    }

    private Type2Tlv.Content content() throws ReaderException
    {
        final byte[] data = new byte[DATA_BYTES];
        for (int page = FIRST_DATA_PAGE; page <= LAST_DATA_PAGE; page++)
        {
            System.arraycopy(readPage(page), 0, data, (page - FIRST_DATA_PAGE) * PAGE_BYTES, PAGE_BYTES);
        }
        try
        {
            return Type2Tlv.inspect(data);
        }
        catch (InvalidNdefException exception)
        {
            throw new ReaderException(INVALID_DATA, "The tag contains an invalid Type 2 TLV message.", exception);
        }
    }

    private Protection protection() throws ReaderException
    {
        final byte[] staticPage = readPage(STATIC_LOCK_PAGE);
        final byte[] dynamicPage = readPage(DYNAMIC_LOCK_PAGE);
        final byte[] config = readPage(CONFIG_PAGE);
        // Both PROT modes protect writes from AUTH0 onward. Read ACCESS as well;
        // inability to inspect protection must fail before any mutation.
        readPage(ACCESS_PAGE);
        return new Protection(Byte.toUnsignedInt(staticPage[2]), Byte.toUnsignedInt(staticPage[3]),
                Byte.toUnsignedInt(dynamicPage[0]), Byte.toUnsignedInt(config[3]),
                (config[0] & MIRROR_MODE_MASK) != 0 && Byte.toUnsignedInt(config[2]) >= FIRST_DATA_PAGE);
    }

    private record Protection(int staticLow, int staticHigh, int dynamic, int auth0, boolean mirrored)
    {
        private boolean allows(final int firstPage, final int lastPage)
        {
            if (mirrored || (auth0 <= LAST_CONFIG_PAGE && lastPage >= auth0))
            {
                return false;
            }
            for (int page = firstPage; page <= lastPage; page++)
            {
                final boolean locked;
                if (page < 8)
                {
                    locked = (staticLow & (1 << page)) != 0;
                }
                else if (page < 16)
                {
                    locked = (staticHigh & (1 << (page - 8))) != 0;
                }
                else
                {
                    locked = (dynamic & (1 << ((page - 16) / 16))) != 0;
                }
                if (locked)
                {
                    return false;
                }
            }
            return true;
        }
    }

    private byte[] readPage(final int page) throws ReaderException
    {
        final ResponseAPDU response = transmit(new CommandAPDU(CLA, READ_BINARY, 0, page, PAGE_BYTES), page);
        final byte[] data = response.getData();
        if (data.length != PAGE_BYTES)
        {
            throw new ReaderException(IO, "The reader returned an unexpected page length at page " + page + ".");
        }
        return data;
    }

    private void writePage(final int page, final byte[] data) throws ReaderException
    {
        if (page < FIRST_DATA_PAGE || page > LAST_DATA_PAGE || data.length != PAGE_BYTES)
        {
            throw new ReaderException(ACCESS_DENIED, "A write outside the NDEF data area was rejected.");
        }
        transmit(new CommandAPDU(CLA, UPDATE_BINARY, 0, page, data), page);
    }

    private ResponseAPDU transmit(final CommandAPDU command, final int page) throws ReaderException
    {
        try
        {
            final ResponseAPDU response = channel.transmit(command);
            if (response.getSW() != SUCCESS)
            {
                throw new ReaderException(ACCESS_DENIED,
                        "Page %d access failed (status %04X).".formatted(page, response.getSW()));
            }
            return response;
        }
        catch (CardException exception)
        {
            throw new ReaderException(IO, "Communication with the tag failed at page " + page + ".", exception);
        }
    }
}
