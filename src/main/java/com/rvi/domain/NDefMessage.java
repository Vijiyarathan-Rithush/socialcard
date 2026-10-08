package com.rvi.domain;

import com.rvi.domain.exception.NDefMessageException;

public record NDefMessage(byte[] data)
{
    private static final byte NDEF_TLV = 0x03;
    private static final byte TERMINATOR = (byte) 0xFE;
    private static final byte TYPE_LENGTH = 0x01;
    private static final byte URI_TYPE = 0x55;
    private static final byte HTTPS_PREFIX = 0x04;

    private static final int TLV_TYPE_OFFSET = 0;
    private static final int TLV_LENGTH_OFFSET = 1;
    private static final int TLV_HEADER_BYTES = 2;
    private static final int TERMINATOR_BYTES = 1;
    private static final int TLV_OVERHEAD = TLV_HEADER_BYTES + TERMINATOR_BYTES;

    private static final int EXTENDED_TLV_LENGTH_MARKER = 0xFF;

    private static final int TYPE_LENGTH_OFFSET = 1;
    private static final int PAYLOAD_LENGTH_OFFSET = 2;
    private static final int URI_TYPE_OFFSET = 3;
    private static final int URI_PREFIX_OFFSET = 4;

    private static final int URI_RECORD_OVERHEAD = 4;
    private static final int MIN_URI_PAYLOAD_LENGTH = 1;
    private static final int MIN_URI_RECORD_LENGTH = URI_RECORD_OVERHEAD + MIN_URI_PAYLOAD_LENGTH;
    private static final int MIN_MESSAGE_LENGTH = TLV_OVERHEAD + MIN_URI_RECORD_LENGTH;

    private static final int MESSAGE_BEGIN_MASK = 0x80;
    private static final int MESSAGE_END_MASK = 0x40;
    private static final int RECORD_FORMAT_MASK = 0x3F;
    private static final int SHORT_URI_RECORD_FORMAT = 0x11;

    public NDefMessage
    {
        if (data == null || data.length < MIN_MESSAGE_LENGTH)
        {
            throw new NDefMessageException("Data is missing or too short");
        }

        data = data.clone();

        if (data[TLV_TYPE_OFFSET] != NDEF_TLV)
        {
            throw new NDefMessageException("Missing NDEF TLV");
        }

        final int ndefLength = Byte.toUnsignedInt(data[TLV_LENGTH_OFFSET]);

        if (ndefLength == EXTENDED_TLV_LENGTH_MARKER)
        {
            throw new NDefMessageException("Extended TLV length is not supported");
        }

        if (ndefLength != data.length - TLV_OVERHEAD)
        {
            throw new NDefMessageException("Invalid NDEF length");
        }

        final int ndefEndExclusive = data.length - TERMINATOR_BYTES;

        if (data[ndefEndExclusive] != TERMINATOR)
        {
            throw new NDefMessageException("Missing terminator");
        }

        int recordOffset = TLV_HEADER_BYTES;
        boolean firstRecord = true;

        while (recordOffset < ndefEndExclusive)
        {
            if (ndefEndExclusive - recordOffset < MIN_URI_RECORD_LENGTH)
            {
                throw new NDefMessageException("Incomplete URI record");
            }

            final int header = Byte.toUnsignedInt(data[recordOffset]);
            final int payloadLength = Byte.toUnsignedInt(data[recordOffset + PAYLOAD_LENGTH_OFFSET]);

            final boolean messageBegin = (header & MESSAGE_BEGIN_MASK) != 0;
            final boolean messageEnd = (header & MESSAGE_END_MASK) != 0;

            if ((header & RECORD_FORMAT_MASK) != SHORT_URI_RECORD_FORMAT || messageBegin != firstRecord)
            {
                throw new NDefMessageException("Invalid record header");
            }

            if (data[recordOffset + TYPE_LENGTH_OFFSET] != TYPE_LENGTH)
            {
                throw new NDefMessageException("Invalid type length");
            }

            if (payloadLength < MIN_URI_PAYLOAD_LENGTH)
            {
                throw new NDefMessageException("Missing URI prefix");
            }

            final int nextRecordOffset = recordOffset + URI_RECORD_OVERHEAD + payloadLength;

            if (nextRecordOffset > ndefEndExclusive)
            {
                throw new NDefMessageException("Invalid payload length");
            }

            if (data[recordOffset + URI_TYPE_OFFSET] != URI_TYPE)
            {
                throw new NDefMessageException("Expected URI record");
            }

            if (data[recordOffset + URI_PREFIX_OFFSET] != HTTPS_PREFIX)
            {
                throw new NDefMessageException("Expected HTTPS prefix");
            }

            if (messageEnd != (nextRecordOffset == ndefEndExclusive))
            {
                throw new NDefMessageException("Invalid message end flag");
            }

            recordOffset = nextRecordOffset;
            firstRecord = false;
        }
    }
}