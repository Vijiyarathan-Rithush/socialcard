package com.rvi.application;

import com.rvi.application.exception.TlvException;
import com.rvi.domain.NDefMessage;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class TlvDecoder
{
    private static final int NDEF_TYPE = 0x03;
    private static final int EXTENDED_LENGTH = 0xFF;
    private static final int TERMINATOR = 0xFE;
    private static final int MAX_NDEF_LENGTH = 0xFFFE;

    public NDefMessage decode(final byte[] data)
    {
        final Header header = readHeader(data);

        if (data.length < header.totalLength())
        {
            throw new TlvException("TLV length exceeds the available data");
        }

        if (Byte.toUnsignedInt(data[header.totalLength() - 1]) != TERMINATOR)
        {
            throw new TlvException("Missing TLV terminator");
        }

        if (header.valueLength() == 0)
        {
            throw new TlvException("Tag contains an empty NDEF TLV");
        }

        return new NDefMessage(Arrays.copyOfRange(data, header.headerLength(), header.headerLength() + header.valueLength()));
    }

    public int encodedLength(final byte[] data)
    {
        return readHeader(data).totalLength();
    }

    private Header readHeader(final byte[] data)
    {
        if (data == null || data.length < 2)
        {
            throw new TlvException("Incomplete TLV header");
        }

        if (Byte.toUnsignedInt(data[0]) != NDEF_TYPE)
        {
            throw new TlvException("Expected an NDEF TLV at the start of memory");
        }

        final int length = Byte.toUnsignedInt(data[1]);

        if (length != EXTENDED_LENGTH)
        {
            return new Header(2, length);
        }

        if (data.length < 4)
        {
            throw new TlvException("Incomplete extended TLV header");
        }

        final int extendedLength = Short.toUnsignedInt(ByteBuffer.wrap(data, 2, 2).getShort());

        if (extendedLength < EXTENDED_LENGTH || extendedLength > MAX_NDEF_LENGTH)
        {
            throw new TlvException("Invalid extended TLV length");
        }

        return new Header(4, extendedLength);
    }

    private record Header(int headerLength, int valueLength)
    {
        int totalLength()
        {
            return headerLength + valueLength + 1;
        }
    }
}
