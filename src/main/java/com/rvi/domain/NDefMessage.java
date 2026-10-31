package com.rvi.domain;

import com.rvi.domain.exception.NDefMessageException;

/** NDEF URI records inside a Type 2 TLV, bounded by NTAG215's factory CC area. */
public record NDefMessage(byte[] data)
{
    // NTAG215 advertises 0x3E * 8 bytes, despite 504 bytes of physical user memory.
    public static final int MAX_NDEF_BYTES = 496;

    public NDefMessage
    {
        if (data == null || data.length < 8 || data.length > MAX_NDEF_BYTES)
            throw new NDefMessageException("NDEF data must fit the 496-byte NTAG215 NDEF area");
        data = data.clone();
        if (data[0] != 0x03) throw new NDefMessageException("Missing NDEF TLV");

        final boolean extended = unsigned(data[1]) == 0xFF;
        final int headerBytes = extended ? 4 : 2;
        final int length = extended ? (unsigned(data[2]) << 8) | unsigned(data[3]) : unsigned(data[1]);
        if (length == 0 || (extended && length < 255) || length != data.length - headerBytes - 1)
            throw new NDefMessageException("Invalid NDEF length");
        final int end = data.length - 1;
        if (unsigned(data[end]) != 0xFE) throw new NDefMessageException("Missing terminator");

        int offset = headerBytes;
        boolean first = true;
        while (offset < end)
        {
            if (end - offset < 5) throw new NDefMessageException("Incomplete URI record");
            final int header = unsigned(data[offset]);
            final boolean shortRecord = (header & 0x10) != 0;
            final int recordHeaderBytes = shortRecord ? 4 : 7;
            if (end - offset < recordHeaderBytes + 1)
                throw new NDefMessageException("Incomplete URI record");
            // TNF=well-known, no chunks or ID; MB only on the first record.
            if ((header & 0x2F) != 0x01 || ((header & 0x80) != 0) != first)
                throw new NDefMessageException("Invalid record header");
            if (data[offset + 1] != 1) throw new NDefMessageException("Invalid type length");

            long payloadLength = unsigned(data[offset + 2]);
            if (!shortRecord)
                for (int index = 3; index <= 5; index++)
                    payloadLength = (payloadLength << 8) | unsigned(data[offset + index]);
            if (payloadLength < 1 || payloadLength > end - offset - recordHeaderBytes)
                throw new NDefMessageException("Invalid payload length");
            final int typeOffset = offset + recordHeaderBytes - 1;
            if (data[typeOffset] != 0x55 || data[typeOffset + 1] != 0x04)
                throw new NDefMessageException("Expected HTTPS URI record");
            final int next = offset + recordHeaderBytes + (int) payloadLength;
            if (((header & 0x40) != 0) != (next == end))
                throw new NDefMessageException("Invalid message end flag");
            offset = next;
            first = false;
        }
    }

    private static int unsigned(final byte value) { return Byte.toUnsignedInt(value); }

    @Override
    public byte[] data() { return data.clone(); }
}
