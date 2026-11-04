package com.rvi.application;

import com.rvi.application.exception.TlvException;
import com.rvi.domain.NDefMessage;

import java.nio.ByteBuffer;

public final class TlvEncoder
{
    private static final int NDEF_TYPE = 0x03;
    private static final int EXTENDED_LENGTH = 0xFF;
    private static final int TERMINATOR = 0xFE;
    private static final int MAX_NDEF_LENGTH = 0xFFFE;

    /** Wraps validated NDEF bytes in an NDEF TLV with a terminator, using extended length when needed. */
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

}
