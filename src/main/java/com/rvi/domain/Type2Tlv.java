package com.rvi.domain;

import com.rvi.exception.InvalidNdefException;
import java.io.ByteArrayOutputStream;
import java.util.Objects;

/** NFC Forum Type 2 TLV framing. Input to the parser starts at physical byte 16. */
public final class Type2Tlv
{
    private static final int DATA_START_BYTE = 16;
    private static final int PHYSICAL_PAGE_BYTES = 4;
    private static final int NULL = 0x00;
    private static final int LOCK_CONTROL = 0x01;
    private static final int MEMORY_CONTROL = 0x02;
    private static final int NDEF = 0x03;
    private static final int PROPRIETARY = 0xFD;
    private static final int TERMINATOR = 0xFE;
    private static final int EXTENDED_LENGTH = 0xFF;
    private static final int MAX_LENGTH = 0xFFFF;

    private Type2Tlv()
    {
    }

    public static int encodedSize(final int ndefBytes)
    {
        if (ndefBytes < 0 || ndefBytes > MAX_LENGTH)
        {
            throw new InvalidNdefException("The NDEF message exceeds the Type 2 TLV length range.");
        }
        return ndefBytes + (ndefBytes < EXTENDED_LENGTH ? 3 : 5);
    }

    /** Type 2 tags commit complete four-byte physical pages. */
    public static int paddedEncodedSize(final int ndefBytes)
    {
        return Math.ceilDiv(encodedSize(ndefBytes), PHYSICAL_PAGE_BYTES) * PHYSICAL_PAGE_BYTES;
    }

    public static byte[] encode(final NdefMessage message)
    {
        final byte[] records = Objects.requireNonNull(message).data();
        final ByteArrayOutputStream result = new ByteArrayOutputStream(encodedSize(records.length));
        result.write(NDEF);
        if (records.length < EXTENDED_LENGTH)
        {
            result.write(records.length);
        }
        else
        {
            result.write(EXTENDED_LENGTH);
            result.write(records.length >>> Byte.SIZE);
            result.write(records.length);
        }
        result.writeBytes(records);
        result.write(TERMINATOR);
        return result.toByteArray();
    }

    public static NdefMessage parse(final byte[] dataArea)
    {
        return inspect(dataArea).message();
    }

    /** Additional TLVs/reservations are readable, but require a layout-aware writer. */
    public record Content(NdefMessage message, boolean hasAdditionalTlvs)
    {
    }

    public static Content inspect(final byte[] dataArea)
    {
        final Cursor cursor = new Cursor(Objects.requireNonNull(dataArea));
        NdefMessage message = null;
        boolean additional = false;
        while (cursor.hasNext())
        {
            final int type = cursor.next();
            if (type == NULL)
            {
                continue;
            }
            if (type == TERMINATOR)
            {
                break;
            }
            final int length = readLength(cursor);
            if (type == LOCK_CONTROL || type == MEMORY_CONTROL)
            {
                if (message != null || length != 3)
                {
                    throw malformed("Control TLVs must precede NDEF and contain three bytes.");
                }
                final int position = cursor.next();
                final int size = cursor.next();
                final int control = cursor.next();
                cursor.reserve(type, position, size, control);
                additional = true;
            }
            else if (type == NDEF)
            {
                if (message != null)
                {
                    throw malformed("A Type 2 data area must not contain multiple NDEF TLVs.");
                }
                message = new NdefMessage(cursor.bytes(length));
            }
            else if (type == PROPRIETARY)
            {
                cursor.bytes(length);
                additional = true;
            }
            else
            {
                throw malformed("The Type 2 data area contains an unknown TLV type.");
            }
        }
        return new Content(message == null ? new NdefMessage(new byte[0]) : message, additional);
    }

    private static int readLength(final Cursor cursor)
    {
        final int length = cursor.next();
        if (length != EXTENDED_LENGTH)
        {
            return length;
        }
        final int extended = (cursor.next() << Byte.SIZE) | cursor.next();
        if (extended < EXTENDED_LENGTH)
        {
            throw malformed("The extended TLV length must be at least 255 bytes.");
        }
        return extended;
    }

    private static InvalidNdefException malformed(final String message)
    {
        return new InvalidNdefException(message);
    }

    private static final class Cursor
    {
        private final byte[] data;
        private final boolean[] reserved;
        private int offset;

        private Cursor(final byte[] data)
        {
            this.data = data;
            reserved = new boolean[data.length];
        }

        private boolean hasNext()
        {
            while (offset < data.length && reserved[offset])
            {
                offset++;
            }
            return offset < data.length;
        }

        private int next()
        {
            if (!hasNext())
            {
                throw malformed("The TLV length exceeds the available data area.");
            }
            return Byte.toUnsignedInt(data[offset++]);
        }

        private byte[] bytes(final int count)
        {
            if (count > data.length - offset)
            {
                throw malformed("The TLV length exceeds the available data area.");
            }
            final byte[] value = new byte[count];
            for (int index = 0; index < count; index++)
            {
                value[index] = (byte) next();
            }
            return value;
        }

        private void reserve(final int type, final int position, final int size, final int control)
        {
            final int pageExponent = control & 0x0F;
            final int pageBytes = 1 << pageExponent;
            final int byteOffset = position & 0x0F;
            if (pageExponent == 0 || byteOffset >= pageBytes
                    || (type == LOCK_CONTROL && (control >>> 4) == 0))
            {
                throw malformed("Invalid control TLV page geometry.");
            }
            final int first = (position >>> 4) * pageBytes + byteOffset - DATA_START_BYTE;
            final int units = size == 0 ? 256 : size;
            final int count = type == LOCK_CONTROL ? (units + 7) / Byte.SIZE : units;
            if (first < offset)
            {
                throw malformed("A control TLV must not reserve bytes already parsed.");
            }
            // Lock areas may sit beyond the CC data area. Never read them as NDEF bytes.
            for (int index = first; index < Math.min(data.length, first + count); index++)
            {
                if (reserved[index])
                {
                    throw malformed("Control TLV reservations overlap.");
                }
                reserved[index] = true;
            }
        }
    }
}
