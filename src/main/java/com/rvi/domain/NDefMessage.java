package com.rvi.domain;

import com.rvi.domain.exception.NDefMessageException;

public record NDefMessage(byte[] data)
{

    private static final byte NDEF_TLV = 0x03;
    private static final byte TERMINATOR = (byte) 0xFE;
    private static final byte TYPE_LENGTH = 0x01;
    private static final byte URI_TYPE = 0x55;
    private static final byte HTTPS_PREFIX = 0x04;

    public NDefMessage
    {
        if (data == null || data.length < 8)
        {
            throw new NDefMessageException("Data is missing or too short");
        }

        data = data.clone();

        if (data[0] != NDEF_TLV)
        {
            throw new NDefMessageException("Missing NDEF TLV");
        }

        final int ndefLength = Byte.toUnsignedInt(data[1]);

        if (ndefLength == 0xFF)
        {
            throw new NDefMessageException(
                    "Extended TLV length is not supported"
            );
        }

        if (ndefLength != data.length - 3)
        {
            throw new NDefMessageException("Invalid NDEF length");
        }

        if (data[data.length - 1] != TERMINATOR)
        {
            throw new NDefMessageException("Missing terminator");
        }

        final int end = data.length - 1;
        int offset = 2;
        boolean first = true;

        while (offset < end)
        {
            if (end - offset < 5)
            {
                throw new NDefMessageException("Incomplete URI record");
            }

            final int header = Byte.toUnsignedInt(data[offset]);
            final int payloadLength =
                    Byte.toUnsignedInt(data[offset + 2]);

            final boolean messageBegin = (header & 0x80) != 0;
            final boolean messageEnd = (header & 0x40) != 0;

            if ((header & 0x3F) != 0x11 || messageBegin != first)
            {
                throw new NDefMessageException("Invalid record header");
            }

            if (data[offset + 1] != TYPE_LENGTH)
            {
                throw new NDefMessageException("Invalid type length");
            }

            if (payloadLength < 1)
            {
                throw new NDefMessageException("Missing URI prefix");
            }

            final int nextOffset = offset + 4 + payloadLength;

            if (nextOffset > end)
            {
                throw new NDefMessageException("Invalid payload length");
            }

            if (data[offset + 3] != URI_TYPE)
            {
                throw new NDefMessageException("Expected URI record");
            }

            if (data[offset + 4] != HTTPS_PREFIX)
            {
                throw new NDefMessageException("Expected HTTPS prefix");
            }

            if (messageEnd != (nextOffset == end))
            {
                throw new NDefMessageException("Invalid message end flag");
            }

            offset = nextOffset;
            first = false;
        }
    }
}