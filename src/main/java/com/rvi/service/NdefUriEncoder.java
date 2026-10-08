package com.rvi.service;

import com.rvi.domain.NDefMessage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static com.rvi.domain.NdefFormat.*;

public final class NdefUriEncoder
{
    public NDefMessage encodeURIs(final List<String> urls)
    {
        if (urls == null || urls.isEmpty())
        {
            throw new IllegalArgumentException("Füge mindestens einen HTTPS-Link hinzu.");
        }

        final ByteArrayOutputStream records = new ByteArrayOutputStream();

        for (int index = 0; index < urls.size(); index++)
        {
            final byte[] suffix = uriBytes(urls.get(index));
            final int payloadLength = suffix.length + 1;

            if (records.size() + RECORD_OVERHEAD + payloadLength > MAX_MESSAGE_BYTES)
            {
                throw new IllegalArgumentException("Die Links überschreiten das Limit von 254 NDEF-Bytes.");
            }

            records.write(header(index, urls.size()));
            records.write(TYPE_LENGTH);
            records.write(payloadLength);
            records.write(URI_TYPE);
            records.write(HTTPS_PREFIX);
            records.writeBytes(suffix);
        }

        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(TLV_TYPE);
        output.write(records.size());
        output.writeBytes(records.toByteArray());
        output.write(TERMINATOR);
        return new NDefMessage(output.toByteArray());
    }

    public List<String> decode(final NDefMessage message)
    {
        final byte[] data = message.data();
        final List<String> urls = new ArrayList<>();
        int offset = TLV_HEADER_BYTES;

        while (offset < data.length - 1)
        {
            final int payloadLength = Byte.toUnsignedInt(data[offset + PAYLOAD_LENGTH_OFFSET]);
            final String suffix = new String(data, offset + URL_OFFSET, payloadLength - 1, StandardCharsets.UTF_8);
            final String url = HTTPS + suffix;
            uriBytes(url);
            urls.add(url);
            offset += RECORD_OVERHEAD + payloadLength;
        }

        return List.copyOf(urls);
    }

    private byte[] uriBytes(final String value)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException("Ein Link ist leer.");
        }

        final URI uri;

        try
        {
            uri = URI.create(value.strip());
        }
        catch (IllegalArgumentException exception)
        {
            throw new IllegalArgumentException("Ungültiger Link: " + value, exception);
        }

        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
        {
            throw new IllegalArgumentException("Verwende einen gültigen HTTPS-Link ohne Zugangsdaten.");
        }

        return uri.toASCIIString().substring(HTTPS.length()).getBytes(StandardCharsets.UTF_8);
    }

    private int header(final int index, final int count)
    {
        if (count == 1)
        {
            return SINGLE_HEADER;
        }

        if (index == 0)
        {
            return FIRST_HEADER;
        }

        if (index == count - 1)
        {
            return LAST_HEADER;
        }

        return MIDDLE_HEADER;
    }
}
