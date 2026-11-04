package com.rvi.application;

import com.rvi.application.exception.NDefEncodingException;
import com.rvi.domain.NDefMessage;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class NDefEncoder
{

    private static final String HTTPS_SCHEME = "https";
    private static final String HTTPS_URI_PREFIX = "https://";

    private static final int MAX_SHORT_PAYLOAD_LENGTH = 0xFF;
    private static final int SHORT_RECORD_MASK = 0x10;

    private static final int TYPE_LENGTH = 0x01;
    private static final int URI_TYPE = 0x55;
    private static final int HTTPS_PREFIX = 0x04;

    private static final int SINGLE_RECORD_HEADER = 0xD1;
    private static final int FIRST_RECORD_HEADER = 0x91;
    private static final int LAST_RECORD_HEADER = 0x51;
    private static final int MIDDLE_RECORD_HEADER = 0x11;

    private static final int URI_PREFIX_BYTES = 1;

    public NDefMessage encodeURI(final String url)
    {
        if (url == null)
        {
            throw new NDefEncodingException("URL is null");
        }

        return encodeURIs(List.of(url));
    }

    public NDefMessage encodeURIs(final List<String> urls)
    {
        if (urls == null || urls.isEmpty())
        {
            throw new NDefEncodingException("URLs are missing");
        }

        final ByteArrayOutputStream records = new ByteArrayOutputStream();

        for (int index = 0; index < urls.size(); index++)
        {
            final String url = urls.get(index);

            if (url == null || url.isBlank())
            {
                throw new NDefEncodingException("URL is null or blank");
            }

            final URI uri;

            try
            {
                uri = URI.create(url.strip());
            }
            catch (IllegalArgumentException exception)
            {
                throw new NDefEncodingException("Invalid URI: " + url, exception);
            }

            final byte[] urlBytes = getUrlBytes(uri);
            final int payloadLength = urlBytes.length + URI_PREFIX_BYTES;
            final boolean shortRecord = payloadLength <= MAX_SHORT_PAYLOAD_LENGTH;
            int header = getRecordHeader(index, urls.size());

            if (!shortRecord)
            {
                header &= ~SHORT_RECORD_MASK;
            }

            records.write(header);
            records.write(TYPE_LENGTH);

            if (shortRecord)
            {
                records.write(payloadLength);
            }
            else
            {
                records.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(payloadLength).array());
            }

            records.write(URI_TYPE);
            records.write(HTTPS_PREFIX);
            records.writeBytes(urlBytes);
        }

        return new NDefMessage(records.toByteArray());
    }

    private static int getRecordHeader(final int index, final int recordCount)
    {
        if (recordCount == 1)
        {
            return SINGLE_RECORD_HEADER;
        }

        if (index == 0)
        {
            return FIRST_RECORD_HEADER;
        }

        if (index == recordCount - 1)
        {
            return LAST_RECORD_HEADER;
        }

        return MIDDLE_RECORD_HEADER;
    }

    private static byte[] getUrlBytes(final URI uri)
    {
        if (!HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
        {
            throw new NDefEncodingException("Expected a valid HTTPS URL");
        }

        final String asciiUrl = uri.toASCIIString();
        final String uriSuffix = asciiUrl.substring(HTTPS_URI_PREFIX.length());
        final byte[] urlBytes = uriSuffix.getBytes(StandardCharsets.UTF_8);

        return urlBytes;
    }
}
