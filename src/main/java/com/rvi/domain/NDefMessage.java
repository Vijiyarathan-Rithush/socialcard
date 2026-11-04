package com.rvi.domain;

import com.rvi.domain.exception.NDefMessageException;
import com.rvi.domain.exception.NDefValueException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public record NDefMessage(byte[] data)
{
    private static final int MESSAGE_BEGIN_MASK = 0x80;
    private static final int MESSAGE_END_MASK = 0x40;
    private static final int CHUNKED_MASK = 0x20;
    private static final int SHORT_RECORD_MASK = 0x10;
    private static final int ID_PRESENT_MASK = 0x08;
    private static final int TYPE_NAME_FORMAT_MASK = 0x07;
    private static final int WELL_KNOWN_TYPE = 0x01;

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

            if (messageBegin != firstRecord || chunked || idPresent || (typeNameFormat != WELL_KNOWN_TYPE && typeNameFormat != 0x02))
            {
                throw new NDefMessageException("Unsupported or invalid record header");
            }

            final long payloadLength = readPayloadLength(input, shortRecord);

            if (typeLength == 0 || payloadLength < 1)
            {
                throw new NDefMessageException("Invalid NDEF content length");
            }

            if (typeLength + payloadLength > input.remaining())
            {
                throw new NDefMessageException("Record exceeds the available data");
            }

            final byte[] type = new byte[typeLength];
            final byte[] payload = new byte[(int) payloadLength];
            input.get(type);
            input.get(payload);

            validateValue(typeNameFormat, type, payload);

            if (messageEnd != !input.hasRemaining())
            {
                throw new NDefMessageException("Invalid message end flag");
            }

            firstRecord = false;
        }
    }

    private static void validateValue(final int typeNameFormat, final byte[] type, final byte[] payload)
    {
        if (typeNameFormat == WELL_KNOWN_TYPE)
        {
            if (type.length != 1)
            {
                throw new NDefMessageException("Expected a one-byte well-known type");
            }

            if (type[0] == 0x55)
            {
                if (Byte.toUnsignedInt(payload[0]) > 0x23)
                {
                    throw new NDefMessageException("Unsupported URI prefix");
                }

                decodeUtf8(Arrays.copyOfRange(payload, 1, payload.length));
            }
            else if (type[0] == 0x54)
            {
                final int status = Byte.toUnsignedInt(payload[0]);
                final int languageLength = status & 0x3F;

                if ((status & 0xC0) != 0 || languageLength == 0 || languageLength + 1 >= payload.length)
                {
                    throw new NDefMessageException("Expected a UTF-8 text value with a language and text");
                }

                final String language = decodeUtf8(Arrays.copyOfRange(payload, 1, languageLength + 1));
                final String text = decodeUtf8(Arrays.copyOfRange(payload, languageLength + 1, payload.length));

                try
                {
                    new NDefText(text, language);
                }
                catch (NDefValueException exception)
                {
                    throw new NDefMessageException("Invalid text language");
                }
            }
            else
            {
                throw new NDefMessageException("Unsupported well-known type");
            }
        }
        else
        {
            final String mimeType = new String(type, StandardCharsets.US_ASCII);

            if (!mimeType.equals("text/vcard") && !mimeType.equals("application/vnd.wfa.wsc"))
            {
                throw new NDefMessageException("Unsupported MIME type");
            }
        }
    }

    private static String decodeUtf8(final byte[] data)
    {
        try
        {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
        }
        catch (CharacterCodingException exception)
        {
            throw new NDefMessageException("Invalid UTF-8 content");
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