package com.rvi.service;

import com.rvi.domain.NdefMessage;
import com.rvi.domain.UriPrefix;
import com.rvi.exception.InvalidLinkException;
import com.rvi.exception.InvalidNdefException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static com.rvi.domain.NdefFormat.*;

public final class NdefUriConverter
{
    public NdefMessage encode(final List<String> urls)
    {
        if (urls == null || urls.isEmpty())
        {
            throw new InvalidLinkException("Add at least one HTTPS link.");
        }

        final ByteArrayOutputStream records = new ByteArrayOutputStream();
        for (int index = 0; index < urls.size(); index++)
        {
            final String value = urls.get(index);
            final URI uri = requireHttpsLink(value == null ? null : value.strip());
            final String asciiUri = "https" + uri.toASCIIString().substring(uri.getScheme().length());
            final UriPrefix prefix = UriPrefix.forUri(asciiUri);
            final byte[] suffix = asciiUri.substring(prefix.value().length()).getBytes(StandardCharsets.UTF_8);
            final long payloadLength = (long) suffix.length + URI_PREFIX_LENGTH;
            final boolean shortRecord = payloadLength <= MAX_SHORT_PAYLOAD_BYTES;
            final int headerLength = shortRecord ? SHORT_RECORD_HEADER_BYTES : NORMAL_RECORD_HEADER_BYTES;

            if ((long) records.size() + headerLength + URI_TYPE_LENGTH + payloadLength > Integer.MAX_VALUE)
            {
                throw new InvalidLinkException("The links exceed the supported message size.");
            }

            int header = WELL_KNOWN_TYPE;
            if (index == 0)
            {
                header |= MESSAGE_BEGIN;
            }
            if (index == urls.size() - 1)
            {
                header |= MESSAGE_END;
            }
            if (shortRecord)
            {
                header |= SHORT_RECORD;
            }

            records.write(header);
            records.write(URI_TYPE_LENGTH);
            writePayloadLength(records, (int) payloadLength, shortRecord);
            records.write(URI_TYPE);
            records.write(prefix.code());
            records.writeBytes(suffix);
        }
        return new NdefMessage(records.toByteArray());
    }

    public List<String> decode(final NdefMessage message)
    {
        if (message == null)
        {
            throw new InvalidNdefException("NDEF message must not be null.");
        }

        final RecordInput input = new RecordInput(message.data());
        final List<String> urls = new ArrayList<>();
        while (input.remaining() > 0)
        {
            final int header = input.readByte();
            final int typeLength = input.readByte();
            final boolean shortRecord = (header & SHORT_RECORD) != 0;
            final long payloadLength = input.readPayloadLength(shortRecord);
            final int idLength = (header & ID_LENGTH_PRESENT) == 0 ? 0 : input.readByte();

            if ((header & CHUNKED) != 0)
            {
                throw new InvalidNdefException("Chunked NDEF records are not supported.");
            }
            if (((header & MESSAGE_BEGIN) != 0) != urls.isEmpty())
            {
                throw new InvalidNdefException("Invalid NDEF message-begin flag.");
            }
            if ((header & TYPE_NAME_FORMAT_MASK) != WELL_KNOWN_TYPE || typeLength != URI_TYPE_LENGTH)
            {
                throw new InvalidNdefException("Only well-known NDEF URI records are supported.");
            }
            if (payloadLength < URI_PREFIX_LENGTH || (long) typeLength + idLength + payloadLength > input.remaining())
            {
                throw new InvalidNdefException("Invalid or truncated NDEF record length.");
            }
            if (input.readByte() != URI_TYPE)
            {
                throw new InvalidNdefException("Only NDEF URI records are supported.");
            }

            input.skip(idLength);
            final UriPrefix prefix = UriPrefix.fromCode(input.readByte());
            final String url = prefix.value() + input.readUtf8((int) payloadLength - URI_PREFIX_LENGTH);
            try
            {
                requireHttpsLink(url);
            }
            catch (InvalidLinkException exception)
            {
                throw new InvalidNdefException("The URI record does not contain a valid HTTPS link.", exception);
            }

            if (((header & MESSAGE_END) != 0) != (input.remaining() == 0))
            {
                throw new InvalidNdefException("Invalid NDEF message-end flag.");
            }
            urls.add(url);
        }
        return List.copyOf(urls);
    }

    private static URI requireHttpsLink(final String value)
    {
        if (value == null || value.isBlank())
        {
            throw new InvalidLinkException("A link is empty.");
        }
        if (!StandardCharsets.UTF_8.newEncoder().canEncode(value))
        {
            throw new InvalidLinkException("The link contains invalid Unicode text.");
        }

        final URI uri;
        try
        {
            uri = URI.create(value);
        }
        catch (IllegalArgumentException ignored)
        {
            // URI parser exceptions retain their input, which must not reach diagnostic logs.
            throw new InvalidLinkException("The link is not a valid URI.");
        }

        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
        {
            throw new InvalidLinkException("Use a valid HTTPS link without credentials.");
        }
        return uri;
    }

    private static void writePayloadLength(final ByteArrayOutputStream output, final int length, final boolean shortRecord)
    {
        if (!shortRecord)
        {
            for (int index = NORMAL_PAYLOAD_LENGTH_BYTES - 1; index > 0; index--)
            {
                output.write(length >>> (index * BITS_PER_BYTE));
            }
        }
        output.write(length);
    }

    private static final class RecordInput
    {
        private final byte[] data;
        private int offset;

        private RecordInput(final byte[] data)
        {
            this.data = data;
        }

        private int remaining()
        {
            return data.length - offset;
        }

        private int readByte()
        {
            requireRemaining(1);
            return Byte.toUnsignedInt(data[offset++]);
        }

        private long readPayloadLength(final boolean shortRecord)
        {
            final int count = shortRecord ? 1 : NORMAL_PAYLOAD_LENGTH_BYTES;
            long length = 0;
            for (int index = 0; index < count; index++)
            {
                length = (length << BITS_PER_BYTE) | readByte();
            }
            return length;
        }

        private void skip(final int count)
        {
            requireRemaining(count);
            offset += count;
        }

        private String readUtf8(final int count)
        {
            requireRemaining(count);
            try
            {
                final String value = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(data, offset, count)).toString();
                offset += count;
                return value;
            }
            catch (CharacterCodingException exception)
            {
                throw new InvalidNdefException("The URI record contains invalid UTF-8 data.", exception);
            }
        }

        private void requireRemaining(final int count)
        {
            if (count < 0 || count > remaining())
            {
                throw new InvalidNdefException("The NDEF record is truncated.");
            }
        }
    }
}
