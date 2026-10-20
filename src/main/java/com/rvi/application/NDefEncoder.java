package com.rvi.application;

import com.rvi.application.exception.NDefEncodingException;
import com.rvi.domain.NDefMessage;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class NDefEncoder
{

    private static final int NDEF_TLV = 0x03;
    private static final int TERMINATOR = 0xFE;

    private static final String HTTPS_SCHEME = "https";
    private static final String HTTPS_URI_PREFIX = "https://";

    private static final int MAX_URI_SUFFIX_BYTES = 249;
    private static final int MAX_SHORT_NDEF_LENGTH = 254;

    private static final int TYPE_LENGTH = 0x01;
    private static final int URI_TYPE = 0x55;
    private static final int HTTPS_PREFIX = 0x04;

    private static final int SINGLE_RECORD_HEADER = 0xD1;
    private static final int FIRST_RECORD_HEADER = 0x91;
    private static final int LAST_RECORD_HEADER = 0x51;
    private static final int MIDDLE_RECORD_HEADER = 0x11;

    private static final int URI_PREFIX_BYTES = 1;
    private static final int URI_RECORD_OVERHEAD = 4;

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
                throw new NDefEncodingException("Invalid URI: " + url);
            }

            final byte[] urlBytes = getUrlBytes(uri);
            final int payloadLength = urlBytes.length + URI_PREFIX_BYTES;
            final int recordLength = payloadLength + URI_RECORD_OVERHEAD;

            if (records.size() + recordLength > MAX_SHORT_NDEF_LENGTH)
            {
                throw new NDefEncodingException("Combined NDEF message exceeds " + MAX_SHORT_NDEF_LENGTH + " bytes");
            }

            records.write(getRecordHeader(index, urls.size()));
            records.write(TYPE_LENGTH);
            records.write(payloadLength);
            records.write(URI_TYPE);
            records.write(HTTPS_PREFIX);
            records.writeBytes(urlBytes);
        }

        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        output.write(NDEF_TLV);
        output.write(records.size());
        output.writeBytes(records.toByteArray());
        output.write(TERMINATOR);

        return new NDefMessage(output.toByteArray());
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

        if (urlBytes.length > MAX_URI_SUFFIX_BYTES)
        {
            throw new NDefEncodingException("URL is too long");
        }

        return urlBytes;
    }
}
