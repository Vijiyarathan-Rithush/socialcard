package com.rvi.domain;

import static com.rvi.domain.NdefFormat.*;

public record NDefMessage(byte[] data)
{
    public NDefMessage
    {
        if (data == null || data.length < TLV_OVERHEAD + MIN_RECORD_BYTES)
        {
            throw new IllegalArgumentException("NDEF-Daten fehlen oder sind zu kurz.");
        }

        data = data.clone();
        final int length = Byte.toUnsignedInt(data[1]);

        if (Byte.toUnsignedInt(data[0]) != TLV_TYPE || length == EXTENDED_LENGTH || length != data.length - TLV_OVERHEAD)
        {
            throw new IllegalArgumentException("Ungültiger oder nicht unterstützter NDEF-TLV-Rahmen.");
        }

        final int endExclusive = data.length - 1;

        if (Byte.toUnsignedInt(data[endExclusive]) != TERMINATOR)
        {
            throw new IllegalArgumentException("NDEF-Terminator fehlt.");
        }

        int recordOffset = TLV_HEADER_BYTES;
        boolean firstRecord = true;

        while (recordOffset < endExclusive)
        {
            if (endExclusive - recordOffset < MIN_RECORD_BYTES)
            {
                throw new IllegalArgumentException("Unvollständiger URI-Record.");
            }

            final int header = Byte.toUnsignedInt(data[recordOffset]);
            final int payloadLength = Byte.toUnsignedInt(data[recordOffset + PAYLOAD_LENGTH_OFFSET]);
            final boolean begin = (header & BEGIN_MASK) != 0;
            final boolean end = (header & END_MASK) != 0;
            final int nextOffset = recordOffset + RECORD_OVERHEAD + payloadLength;

            if ((header & FORMAT_MASK) != URI_RECORD_FORMAT || begin != firstRecord)
            {
                throw new IllegalArgumentException("Ungültiger Record-Header.");
            }

            if (data[recordOffset + TYPE_LENGTH_OFFSET] != TYPE_LENGTH || payloadLength < 1 || nextOffset > endExclusive)
            {
                throw new IllegalArgumentException("Ungültige Record-Länge.");
            }

            if (data[recordOffset + URI_TYPE_OFFSET] != URI_TYPE || data[recordOffset + PREFIX_OFFSET] != HTTPS_PREFIX)
            {
                throw new IllegalArgumentException("Nur HTTPS-URI-Records werden unterstützt.");
            }

            if (end != (nextOffset == endExclusive))
            {
                throw new IllegalArgumentException("Ungültiges Message-End-Flag.");
            }

            recordOffset = nextOffset;
            firstRecord = false;
        }
    }

    @Override
    public byte[] data()
    {
        return data.clone();
    }
}
