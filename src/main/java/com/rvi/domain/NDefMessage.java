package com.rvi.domain;

import com.rvi.domain.exception.NDefMessageException;

public record NDefMessage(byte[] data)
{

    private static final byte NDEF_TLV = 0x03;
    private static final byte TERMINATOR = (byte) 0xFE;
    private static final byte RECORD_HEADER = (byte) 0xD1;
    private static final byte TYPE_LENGTH = 0x01;
    private static final byte URI_TYPE = 0x55;
    private static final byte HTTPS_PREFIX = 0x04;

    public NDefMessage
    {
        if (data == null || data.length < 8)
        {
            throw new NDefMessageException("Data is missing or too short");
        }

        if (data[0] != NDEF_TLV)
        {
            throw new NDefMessageException("Missing NDEF TLV");
        }

        final int ndefLength = Byte.toUnsignedInt(data[1]);

        if (ndefLength == 0xFF)
        {
            throw new NDefMessageException("Extended TLV length is not supported");
        }

        if (ndefLength != data.length - 3)
        {
            throw new NDefMessageException("Invalid NDEF length");
        }

        if (data[2] != RECORD_HEADER)
        {
            throw new NDefMessageException("Invalid record header");
        }

        if (data[3] != TYPE_LENGTH)
        {
            throw new NDefMessageException("Invalid type length");
        }

        if (Byte.toUnsignedInt(data[4]) != ndefLength - 4)
        {
            throw new NDefMessageException("Invalid payload length");
        }

        if (data[5] != URI_TYPE)
        {
            throw new NDefMessageException("Expected URI record");
        }

        if (data[6] != HTTPS_PREFIX)
        {
            throw new NDefMessageException("Expected HTTPS prefix");
        }

        if (data[data.length - 1] != TERMINATOR)
        {
            throw new NDefMessageException("Missing terminator");
        }

        data = data.clone();
    }

    @Override
    public byte[] data()
    {
        return data.clone();
    }
}