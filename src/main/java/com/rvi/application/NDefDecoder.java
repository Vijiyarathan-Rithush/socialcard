package com.rvi.application;

import com.rvi.domain.NDefContent;
import com.rvi.domain.NDefMessage;
import com.rvi.domain.NDefUri;
import com.rvi.domain.NDefText;
import com.rvi.domain.NDefContact;
import com.rvi.domain.NDefWifi;
import com.rvi.domain.WifiAuthentication;
import com.rvi.domain.WifiEncryption;
import com.rvi.domain.exception.NDefMessageException;
import com.rvi.domain.exception.NDefValueException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Decodes supported NDEF records without requiring a reader or a TLV wrapper. */
public final class NDefDecoder
{
    private static final List<String> URI_PREFIXES = List.of(
            "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:",
            "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://", "sftp://", "smb://", "nfs://",
            "ftp://", "dav://", "news:", "telnet://", "imap:", "rtsp://", "urn:", "pop:",
            "sip:", "sips:", "tftp:", "btspp://", "btl2cap://", "btgoep://", "tcpobex://",
            "irdaobex://", "file://", "urn:epc:id:", "urn:epc:tag:", "urn:epc:pat:",
            "urn:epc:raw:", "urn:epc:", "urn:nfc:");

    /**
     * Returns immutable typed content in record order. vCards support SocialCard's
     * name, email, text phone and website fields; unsupported fields are rejected.
     * WSC decoding supports a single open or WPA2/AES credential.
     * @param message validated NDEF message, not null
     * @return decoded content
     * @throws NDefMessageException if a payload cannot be represented by the domain model
     */
    public List<NDefContent> decode(final NDefMessage message)
    {
        return decode(message, false);
    }

    private List<NDefContent> decode(final NDefMessage message, final boolean urisOnly)
    {
        Objects.requireNonNull(message, "NDEF message is missing");
        final ByteBuffer input = ByteBuffer.wrap(message.data());
        final List<NDefContent> contents = new ArrayList<>();
        try
        {
            while (input.hasRemaining())
            {
                final int header = Byte.toUnsignedInt(input.get());
                final int typeLength = Byte.toUnsignedInt(input.get());
                final int length = (header & 0x10) != 0 ? Byte.toUnsignedInt(input.get()) : input.getInt();
                final byte[] type = new byte[typeLength];
                final byte[] payload = new byte[length];
                input.get(type);
                input.get(payload);
                if ((header & 7) == 1 && type[0] == 0x55)
                {
                    contents.add(new NDefUri(URI_PREFIXES.get(Byte.toUnsignedInt(payload[0]))
                            + new String(payload, 1, payload.length - 1, StandardCharsets.UTF_8)));
                }
                else if (urisOnly)
                {
                    continue;
                }
                else if ((header & 7) == 1 && type[0] == 0x54)
                {
                    final int languageLength = payload[0] & 0x3F;
                    contents.add(new NDefText(new String(payload, 1 + languageLength,
                            payload.length - 1 - languageLength, StandardCharsets.UTF_8),
                            new String(payload, 1, languageLength, StandardCharsets.US_ASCII)));
                }
                else if (new String(type, StandardCharsets.US_ASCII).equals("text/vcard"))
                {
                    contents.add(decodeContact(utf8(payload)));
                }
                else
                {
                    contents.add(decodeWifi(payload));
                }
            }
        }
        catch (NDefValueException exception)
        {
            throw new NDefMessageException("NDEF payload cannot be represented: " + exception.getMessage());
        }
        return List.copyOf(contents);
    }

    /** Returns only URI strings in record order; other supported records are skipped. */
    public List<String> decodeURIs(final NDefMessage message)
    {
        return decode(message, true).stream().filter(NDefUri.class::isInstance)
                .map(NDefUri.class::cast).map(NDefUri::uri).toList();
    }

    private String utf8(final byte[] value)
    {
        try
        {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(value)).toString();
        }
        catch (CharacterCodingException exception)
        {
            throw new NDefMessageException("Invalid UTF-8 payload");
        }
    }

    private NDefContact decodeContact(final String payload)
    {
        final String unfolded = payload.replaceAll("\\r?\\n[ \\t]", "");
        final Map<String, String> fields = new HashMap<>();
        for (final String line : unfolded.split("\\r?\\n"))
        {
            final int colon = line.indexOf(':');
            if (colon < 1)
            {
                throw new NDefMessageException("Invalid vCard property");
            }
            final String key = line.substring(0, colon);
            final String value = line.substring(colon + 1);
            if (!List.of("BEGIN", "VERSION", "FN", "N", "EMAIL", "TEL;VALUE=text", "URL", "END").contains(key)
                    || fields.putIfAbsent(key, value) != null)
            {
                throw new NDefMessageException("Unsupported or duplicate vCard property: " + key);
            }
        }
        if (!"VCARD".equals(fields.get("BEGIN")) || !"VCARD".equals(fields.get("END"))
                || !"4.0".equals(fields.get("VERSION")) || !fields.containsKey("FN"))
        {
            throw new NDefMessageException("Expected a vCard 4.0 with a formatted name");
        }
        final List<String> names = splitEscaped(fields.getOrDefault("N", ""));
        if (names.size() != 5 || !names.get(2).isEmpty() || !names.get(3).isEmpty() || !names.get(4).isEmpty())
        {
            throw new NDefMessageException("Expected a vCard structured first and last name only");
        }
        return new NDefContact(names.get(1), names.get(0), unescape(fields.getOrDefault("EMAIL", "")),
                unescape(fields.getOrDefault("TEL;VALUE=text", "")), fields.getOrDefault("URL", ""));
    }

    private List<String> splitEscaped(final String value)
    {
        final List<String> parts = new ArrayList<>();
        final StringBuilder part = new StringBuilder();
        boolean escaped = false;
        for (final char character : value.toCharArray())
        {
            if (!escaped && character == ';')
            {
                parts.add(unescape(part.toString()));
                part.setLength(0);
                continue;
            }
            part.append(character);
            if (escaped) escaped = false;
            else if (character == '\\') escaped = true;
        }
        parts.add(unescape(part.toString()));
        return parts;
    }

    private String unescape(final String value)
    {
        final StringBuilder output = new StringBuilder();
        boolean escaped = false;
        for (final char character : value.toCharArray())
        {
            if (escaped)
            {
                if (character != 'n' && character != 'N' && character != '\\' && character != ',' && character != ';')
                    throw new NDefMessageException("Unsupported vCard escape");
                output.append(character == 'n' || character == 'N' ? '\n' : character);
                escaped = false;
            }
            else if (character == '\\') escaped = true;
            else output.append(character);
        }
        if (escaped) throw new NDefMessageException("Incomplete vCard escape");
        return output.toString();
    }

    private NDefWifi decodeWifi(final byte[] payload)
    {
        final Map<Integer, byte[]> outer = attributes(payload);
        final byte[] credential = required(outer, 0x100E);
        final Map<Integer, byte[]> fields = attributes(credential);
        final int authentication = unsignedShort(required(fields, 0x1003));
        final int encryption = unsignedShort(required(fields, 0x100F));
        if (!((authentication == 1 && encryption == 1) || (authentication == 0x20 && encryption == 8)))
            throw new NDefMessageException("Only open or WPA2-Personal/AES Wi-Fi credentials are supported");
        return new NDefWifi(utf8(required(fields, 0x1045)),
                authentication == 1 ? WifiAuthentication.OPEN : WifiAuthentication.WPA2_PERSONAL,
                encryption == 1 ? WifiEncryption.NONE : WifiEncryption.AES,
                new String(required(fields, 0x1027), StandardCharsets.US_ASCII));
    }

    private Map<Integer, byte[]> attributes(final byte[] payload)
    {
        final ByteBuffer input = ByteBuffer.wrap(payload);
        final Map<Integer, byte[]> fields = new HashMap<>();
        while (input.hasRemaining())
        {
            if (input.remaining() < 4) throw new NDefMessageException("Incomplete Wi-Fi attribute header");
            final int type = Short.toUnsignedInt(input.getShort());
            final int length = Short.toUnsignedInt(input.getShort());
            if (length > input.remaining()) throw new NDefMessageException("Wi-Fi attribute exceeds payload");
            final byte[] value = new byte[length];
            input.get(value);
            if (fields.putIfAbsent(type, value) != null)
                throw new NDefMessageException("Duplicate Wi-Fi attribute; multiple credentials are unsupported");
        }
        return fields;
    }

    private byte[] required(final Map<Integer, byte[]> fields, final int key)
    {
        final byte[] value = fields.get(key);
        if (value == null) throw new NDefMessageException("Missing Wi-Fi attribute: " + Integer.toHexString(key));
        return value;
    }

    private int unsignedShort(final byte[] value)
    {
        if (value.length != 2) throw new NDefMessageException("Expected a two-byte Wi-Fi attribute");
        return Short.toUnsignedInt(ByteBuffer.wrap(value).getShort());
    }
}
