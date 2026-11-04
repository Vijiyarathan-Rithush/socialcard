package com.rvi.domain;

import com.rvi.domain.exception.NDefMessageException;

import java.nio.ByteBuffer;

public record NDefMessage(byte[] data)
{
    private static final int URI_TYPE = 0x55;
    private static final int HTTPS_PREFIX = 0x04;
    private static final int MESSAGE_BEGIN_MASK = 0x80;
    private static final int MESSAGE_END_MASK = 0x40;
    private static final int CHUNKED_MASK = 0x20;
    private static final int SHORT_RECORD_MASK = 0x10;
    private static final int ID_PRESENT_MASK = 0x08;
    private static final int TYPE_NAME_FORMAT_MASK = 0x07;
    private static final int WELL_KNOWN_TYPE = 0x01;

    /** Stores only NDEF bytes, without a TLV header, terminator or page padding. */
    public NDefMessage
    {
        if (data == null || data.length == 0)
        {
            throw new NDefMessageException("NDEF data is missing or empty");
        }

        data = data.clone();

        final ByteBuffer input = ByteBuffer.wrap(data);
        boolean firstRecord = true;

        while (input.hasRemaining())
        {
            if (input.remaining() < 2)
            {
                throw new NDefMessageException("Incomplete record header");
            }

            final int header = Byte.toUnsignedInt(input.get());
            final int typeLength = Byte.toUnsignedInt(input.get());
            final boolean messageBegin = (header & MESSAGE_BEGIN_MASK) != 0;
            final boolean messageEnd = (header & MESSAGE_END_MASK) != 0;
            final boolean chunked = (header & CHUNKED_MASK) != 0;
            final boolean shortRecord = (header & SHORT_RECORD_MASK) != 0;
            final boolean idPresent = (header & ID_PRESENT_MASK) != 0;
            final int typeNameFormat = header & TYPE_NAME_FORMAT_MASK;

            if (messageBegin != firstRecord || chunked || idPresent || typeNameFormat != WELL_KNOWN_TYPE)
            {
                throw new NDefMessageException("Unsupported or invalid record header");
            }

            final long payloadLength = readPayloadLength(input, shortRecord);

            if (typeLength != 1 || payloadLength < 1)
            {
                throw new NDefMessageException("Invalid URI record length");
            }

            if (typeLength + payloadLength > input.remaining())
            {
                throw new NDefMessageException("Record exceeds the available data");
            }

            if (Byte.toUnsignedInt(input.get()) != URI_TYPE)
            {
                throw new NDefMessageException("Expected a URI record");
            }

            if (Byte.toUnsignedInt(input.get()) != HTTPS_PREFIX)
            {
                throw new NDefMessageException("Expected the HTTPS URI prefix");
            }

            input.position(input.position() + (int) payloadLength - 1);

            if (messageEnd != !input.hasRemaining())
            {
                throw new NDefMessageException("Invalid message end flag");
            }

            firstRecord = false;
        }
    }

    private static long readPayloadLength(final ByteBuffer input, final boolean shortRecord)
    {
        if (shortRecord)
        {
            if (input.remaining() < 1)
            {
                throw new NDefMessageException("Incomplete payload length");
            }

            return Byte.toUnsignedInt(input.get());
        }
        else
        {
            if (input.remaining() < Integer.BYTES)
            {
                throw new NDefMessageException("Incomplete payload length");
            }

            return Integer.toUnsignedLong(input.getInt());
        }
    }

    @Override
    public byte[] data()
    {
        return data.clone();
    }
}