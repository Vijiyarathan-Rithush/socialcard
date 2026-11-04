package com.rvi.application;

import com.rvi.application.exception.TlvException;
import com.rvi.domain.NDefMessage;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Handles the SocialCard layout: an NDEF TLV at offset zero followed by a terminator. */
public final class TlvCodec
{
    private static final int NDEF_TYPE = 0x03;
    private static final int EXTENDED_LENGTH = 0xFF;
    private static final int TERMINATOR = 0xFE;
    private static final int MAX_NDEF_LENGTH = 0xFFFE;

    public byte[] encode(final NDefMessage message)
    {
        if (message == null)
        {
            throw new TlvException("NDEF message is missing");
        }

        final byte[] data = message.data();

        if (data.length > MAX_NDEF_LENGTH)
        {
            throw new TlvException("NDEF message exceeds the supported TLV length");
        }

        final boolean extended = data.length >= EXTENDED_LENGTH;
        final int overhead;

        if (extended)
        {
            overhead = 5;
        }
        else
        {
            overhead = 3;
        }

        final ByteBuffer output = ByteBuffer.allocate(data.length + overhead);
        output.put((byte) NDEF_TYPE);

        if (extended)
        {
            output.put((byte) EXTENDED_LENGTH);
            output.putShort((short) data.length);
        }
        else
        {
            output.put((byte) data.length);
        }

        output.put(data);
        output.put((byte) TERMINATOR);

        return output.array();
    }

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

    /** Returns the size including the terminator; only the complete header is needed. */
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
