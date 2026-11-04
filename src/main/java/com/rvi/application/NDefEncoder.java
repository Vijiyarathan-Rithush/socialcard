package com.rvi.application;

import com.rvi.application.exception.NDefEncodingException;
import com.rvi.domain.NDefContact;
import com.rvi.domain.NDefContent;
import com.rvi.domain.NDefMessage;
import com.rvi.domain.NDefText;
import com.rvi.domain.NDefUri;
import com.rvi.domain.NDefWifi;
import com.rvi.domain.WifiAuthentication;
import com.rvi.domain.exception.NDefValueException;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class NDefEncoder
{
    private static final int MAX_SHORT_PAYLOAD_LENGTH = 0xFF;
    private static final int SHORT_RECORD_MASK = 0x10;
    private static final int MESSAGE_BEGIN_MASK = 0x80;
    private static final int MESSAGE_END_MASK = 0x40;

    /** Encodes one absolute URI without hardware; accepts the same schemes as NDefUri. */
    public NDefMessage encodeURI(final String url)
    {
        if (url == null)
        {
            throw new NDefEncodingException("URL is null");
        }

        return encodeURIs(List.of(url));
    }

    /** Encodes a nonempty URI list in order. Null, blank and relative URIs are rejected. */
    public NDefMessage encodeURIs(final List<String> urls)
    {
        if (urls == null || urls.isEmpty())
        {
            throw new NDefEncodingException("URLs are missing");
        }

        final List<NDefContent> contents = new ArrayList<>();

        for (final String url : urls)
        {
            if (url == null || url.isBlank())
            {
                throw new NDefEncodingException("URL is null or blank");
            }

            try
            {
                final NDefUri value = new NDefUri(url);

                contents.add(value);
            }
            catch (NDefValueException exception)
            {
                throw new NDefEncodingException("Invalid URI: " + exception.getMessage(), exception);
            }
        }

        return encode(contents);
    }

    /** Encodes a nonempty list of typed content in order, choosing short or long record lengths automatically. */
    public NDefMessage encode(final List<NDefContent> contents)
    {
        if (contents == null || contents.isEmpty())
        {
            throw new NDefEncodingException("NDEF contents are missing");
        }

        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        for (int index = 0; index < contents.size(); index++)
        {
            final NDefContent content = contents.get(index);

            if (content == null)
            {
                throw new NDefEncodingException("NDEF content is missing");
            }

            final EncodedValue value = switch (content)
            {
                case NDefUri uri -> encodeUri(uri);
                case NDefText text -> encodeText(text);
                case NDefContact contact -> encodeContact(contact);
                case NDefWifi wifi -> encodeWifi(wifi);
            };

            final boolean shortPayload = value.payload().length <= MAX_SHORT_PAYLOAD_LENGTH;
            int header = value.typeNameFormat();

            if (index == 0)
            {
                header |= MESSAGE_BEGIN_MASK;
            }

            if (index == contents.size() - 1)
            {
                header |= MESSAGE_END_MASK;
            }

            if (shortPayload)
            {
                header |= SHORT_RECORD_MASK;
            }

            output.write(header);
            output.write(value.type().length);

            if (shortPayload)
            {
                output.write(value.payload().length);
            }
            else
            {
                output.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(value.payload().length).array());
            }

            output.writeBytes(value.type());
            output.writeBytes(value.payload());
        }

        return new NDefMessage(output.toByteArray());
    }

    private EncodedValue encodeUri(final NDefUri value)
    {
        final String uri = value.uri();
        final String suffix;
        final int prefix;

        if (uri.regionMatches(true, 0, "https://", 0, 8))
        {
            prefix = 0x04;
            suffix = uri.substring(8);
        }
        else if (uri.regionMatches(true, 0, "http://", 0, 7))
        {
            prefix = 0x03;
            suffix = uri.substring(7);
        }
        else
        {
            prefix = 0x00;
            suffix = uri;
        }

        final ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(prefix);
        payload.writeBytes(suffix.getBytes(StandardCharsets.UTF_8));

        return new EncodedValue(0x01, new byte[] {0x55}, payload.toByteArray());
    }

    private EncodedValue encodeText(final NDefText value)
    {
        final byte[] language = value.language().getBytes(StandardCharsets.US_ASCII);
        final ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(language.length);
        payload.writeBytes(language);
        payload.writeBytes(value.text().getBytes(StandardCharsets.UTF_8));

        return new EncodedValue(0x01, new byte[] {0x54}, payload.toByteArray());
    }

    private EncodedValue encodeContact(final NDefContact value)
    {
        final StringBuilder text = new StringBuilder();
        appendLine(text, "BEGIN:VCARD");
        appendLine(text, "VERSION:4.0");
        appendLine(text, "FN:" + escape(value.firstName() + " " + value.lastName()).strip());
        appendLine(text, "N:" + escapeComponent(value.lastName()) + ";" + escapeComponent(value.firstName()) + ";;;");

        if (!value.email().isEmpty())
        {
            appendLine(text, "EMAIL:" + escape(value.email()));
        }

        if (!value.phone().isEmpty())
        {
            appendLine(text, "TEL;VALUE=text:" + escape(value.phone()));
        }

        if (!value.website().isEmpty())
        {
            appendLine(text, "URL:" + value.website());
        }

        appendLine(text, "END:VCARD");

        return new EncodedValue(0x02, "text/vcard".getBytes(StandardCharsets.US_ASCII), text.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String escape(final String value)
    {
        return value.replace("\\", "\\\\").replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\\n").replace(",", "\\,");
    }

    private String escapeComponent(final String value)
    {
        return escape(value).replace(";", "\\;");
    }

    private void appendLine(final StringBuilder output, final String line)
    {
        int octets = 0;

        for (final int codePoint : line.codePoints().toArray())
        {
            final String character = new String(Character.toChars(codePoint));
            final int bytes = character.getBytes(StandardCharsets.UTF_8).length;

            if (octets + bytes > 75)
            {
                output.append("\r\n ");
                octets = 1;
            }

            output.append(character);
            octets += bytes;
        }

        output.append("\r\n");
    }

    private EncodedValue encodeWifi(final NDefWifi value)
    {
        final ByteArrayOutputStream credential = new ByteArrayOutputStream();
        attribute(credential, 0x1026, new byte[] {1});
        attribute(credential, 0x1045, value.ssid().getBytes(StandardCharsets.UTF_8));

        if (value.authentication() == WifiAuthentication.OPEN)
        {
            attribute(credential, 0x1003, new byte[] {0, 1});
            attribute(credential, 0x100F, new byte[] {0, 1});
        }
        else
        {
            attribute(credential, 0x1003, new byte[] {0, 0x20});
            attribute(credential, 0x100F, new byte[] {0, 8});
        }

        attribute(credential, 0x1027, value.password().getBytes(StandardCharsets.US_ASCII));
        attribute(credential, 0x1020, new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});

        final ByteArrayOutputStream payload = new ByteArrayOutputStream();
        attribute(payload, 0x104A, new byte[] {0x10});
        attribute(payload, 0x100E, credential.toByteArray());

        return new EncodedValue(0x02, "application/vnd.wfa.wsc".getBytes(StandardCharsets.US_ASCII), payload.toByteArray());
    }

    private void attribute(final ByteArrayOutputStream output, final int type, final byte[] data)
    {
        output.writeBytes(ByteBuffer.allocate(4).putShort((short) type).putShort((short) data.length).array());
        output.writeBytes(data);
    }

    private record EncodedValue(int typeNameFormat, byte[] type, byte[] payload)
    {
    }
}
