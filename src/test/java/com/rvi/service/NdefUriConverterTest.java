package com.rvi.service;

import com.rvi.domain.NdefMessage;
import com.rvi.exception.InvalidLinkException;
import com.rvi.exception.InvalidNdefException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NdefUriConverterTest
{
    private final NdefUriConverter converter = new NdefUriConverter();

    @Test
    void encodesRawNdefWithoutTagFraming()
    {
        assertArrayEquals(new byte[] {(byte) 0xD1, 1, 4, 0x55, 4, 'a', '.', 'b'},
                converter.encode(List.of("https://a.b")).data());
    }

    @Test
    void roundTripsSeveralLinksAndKeepsTheirOrder()
    {
        final List<String> urls = List.of("https://example.com/one", "https://www.example.org/two?x=y", "https://example.net/three#section");
        assertEquals(urls, converter.decode(converter.encode(urls)));
        assertThrows(UnsupportedOperationException.class, () -> converter.decode(converter.encode(urls)).add("https://a.b"));
    }

    @Test
    void normalizesUserWhitespaceSchemeAndUnicodePath()
    {
        assertEquals(List.of("https://example.com/caf%C3%A9"),
                converter.decode(converter.encode(List.of("  HTTPS://example.com/café  "))));
    }

    @Test
    void acceptsEveryHttpsPrefixVariant()
    {
        assertEquals(List.of("https://www.example.com/"), converter.decode(uriRecord(0, "https://www.example.com/")));
        assertEquals(List.of("https://www.example.com/"), converter.decode(uriRecord(2, "example.com/")));
        assertEquals(List.of("https://www.example.com/"), converter.decode(uriRecord(4, "www.example.com/")));
        assertEquals(2, Byte.toUnsignedInt(converter.encode(List.of("https://www.example.com/")).data()[4]));
    }

    @Test
    void acceptsValidUtf8InAnExternalRecord()
    {
        assertEquals(List.of("https://example.com/café"), converter.decode(uriRecord(4, "example.com/café")));
    }

    @Test
    void decodesAnEmptyMessage()
    {
        assertEquals(List.of(), converter.decode(new NdefMessage(new byte[0])));
        assertThrows(InvalidNdefException.class, () -> converter.decode(null));
    }

    @Test
    void rejectsInvalidUserLinks()
    {
        for (final String url : List.of("", "  ", "http://example.com", "https://user:secret@example.com", "https:///missing-host", "not a uri",
                "https://example.com/\uD800", "https://example.com/\uDC00"))
        {
            assertThrows(InvalidLinkException.class, () -> converter.encode(List.of(url)), url);
        }
        assertThrows(InvalidLinkException.class, () -> converter.encode(null));
        assertThrows(InvalidLinkException.class, () -> converter.encode(List.of()));
        assertThrows(InvalidLinkException.class, () -> converter.encode(Arrays.asList((String) null)));
    }

    @Test
    void rejectsNonHttpsAndMalformedExternalLinks()
    {
        for (final String url : List.of("http://example.com", "https://user@example.com", "https:///missing-host", "https://example.com/ "))
        {
            assertThrows(InvalidNdefException.class, () -> converter.decode(uriRecord(0, url)), url);
        }
        assertThrows(InvalidNdefException.class, () -> converter.decode(uriRecord(3, "example.com")));
    }

    @Test
    void reportsInvalidUtf8InsteadOfChangingTheUrl()
    {
        final byte[] bytes = uriRecord(4, "example.com/x").data();
        bytes[bytes.length - 1] = (byte) 0xFF;
        final InvalidNdefException exception = assertThrows(InvalidNdefException.class,
                () -> converter.decode(new NdefMessage(bytes)));
        assertTrue(exception.getMessage().contains("UTF-8"));
        assertNotNull(exception.getCause());
    }

    @Test
    void malformedUserAndCardUrisDoNotExposeSecretsThroughExceptionCauses()
    {
        final String secret = "private-token-123";
        final String malformed = "https://example.com/?token=" + secret + " invalid";
        final InvalidLinkException inputFailure = assertThrows(InvalidLinkException.class,
                () -> converter.encode(List.of(malformed)));
        final InvalidNdefException cardFailure = assertThrows(InvalidNdefException.class,
                () -> converter.decode(uriRecord(0, malformed)));

        for (final Throwable failure : List.of(inputFailure, cardFailure))
        {
            for (Throwable cause = failure; cause != null; cause = cause.getCause())
            {
                assertFalse(cause.toString().contains(secret));
                assertFalse(cause.toString().contains(malformed));
            }
        }
    }

    @Test
    void usesShortRecordsThrough255PayloadBytesThenNormalRecords()
    {
        final String shortUrl = "https://example.com/" + "a".repeat(242);
        final String longUrl = shortUrl + "a";
        final NdefMessage shortMessage = converter.encode(List.of(shortUrl));
        final NdefMessage longMessage = converter.encode(List.of(longUrl));
        assertEquals(0xD1, Byte.toUnsignedInt(shortMessage.data()[0]));
        assertEquals(255, Byte.toUnsignedInt(shortMessage.data()[2]));
        assertEquals(0xC1, Byte.toUnsignedInt(longMessage.data()[0]));
        assertArrayEquals(new byte[] {0, 0, 1, 0}, Arrays.copyOfRange(longMessage.data(), 2, 6));
        assertEquals(List.of(shortUrl), converter.decode(shortMessage));
        assertEquals(List.of(longUrl), converter.decode(longMessage));
    }

    @Test
    void leavesPhysicalCapacityChecksToTheReader()
    {
        final String url = "https://example.com/" + "a".repeat(600);
        final NdefMessage message = converter.encode(List.of(url));
        assertTrue(message.size() > 496);
        assertEquals(List.of(url), converter.decode(message));
    }

    @Test
    void rejectsEveryTruncatedPrefixOfAShortOrNormalRecord()
    {
        for (final String url : List.of("https://example.com/", "https://example.com/" + "a".repeat(300)))
        {
            final byte[] bytes = converter.encode(List.of(url)).data();
            for (int length = 1; length < bytes.length; length++)
            {
                final NdefMessage truncated = new NdefMessage(Arrays.copyOf(bytes, length));
                assertThrows(InvalidNdefException.class, () -> converter.decode(truncated), "Truncated length " + length);
            }
        }
    }

    @Test
    void rejectsUnsignedLengthOverflowAndZeroPayload()
    {
        assertThrows(InvalidNdefException.class, () -> converter.decode(new NdefMessage(
                new byte[] {(byte) 0xC1, 1, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x55, 4})));
        assertThrows(InvalidNdefException.class, () -> converter.decode(new NdefMessage(
                new byte[] {(byte) 0xD1, 1, 0, 0x55})));
    }

    @Test
    void rejectsInvalidFlagsAndUnsupportedRecordTypes()
    {
        final byte[] valid = converter.encode(List.of("https://example.com/")).data();
        for (final int header : new int[] {0x51, 0x91, 0xF1, 0xD2})
        {
            final byte[] bytes = valid.clone();
            bytes[0] = (byte) header;
            assertThrows(InvalidNdefException.class, () -> converter.decode(new NdefMessage(bytes)));
        }
        final byte[] textRecord = valid.clone();
        textRecord[3] = 0x54;
        assertThrows(InvalidNdefException.class, () -> converter.decode(new NdefMessage(textRecord)));
        final byte[] invalidTypeLength = valid.clone();
        invalidTypeLength[1] = 2;
        assertThrows(InvalidNdefException.class, () -> converter.decode(new NdefMessage(invalidTypeLength)));
    }

    @Test
    void rejectsPrematureEndAndRepeatedBeginFlags()
    {
        final NdefMessage one = converter.encode(List.of("https://example.com/"));
        final byte[] twoStandaloneMessages = new byte[one.size() * 2];
        System.arraycopy(one.data(), 0, twoStandaloneMessages, 0, one.size());
        System.arraycopy(one.data(), 0, twoStandaloneMessages, one.size(), one.size());
        assertThrows(InvalidNdefException.class, () -> converter.decode(new NdefMessage(twoStandaloneMessages)));
        twoStandaloneMessages[0] = (byte) 0x91;
        assertThrows(InvalidNdefException.class, () -> converter.decode(new NdefMessage(twoStandaloneMessages)));
    }

    @Test
    void supportsAnOptionalRecordIdentifier()
    {
        final byte[] original = uriRecord(4, "example.com/").data();
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0xD9);
        bytes.write(original[1]);
        bytes.write(original[2]);
        bytes.write(2);
        bytes.write(original[3]);
        bytes.write('i');
        bytes.write('d');
        bytes.writeBytes(Arrays.copyOfRange(original, 4, original.length));
        assertEquals(List.of("https://example.com/"), converter.decode(new NdefMessage(bytes.toByteArray())));
    }

    private static NdefMessage uriRecord(final int prefix, final String suffix)
    {
        final byte[] payload = suffix.getBytes(StandardCharsets.UTF_8);
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0xD1);
        bytes.write(1);
        bytes.write(payload.length + 1);
        bytes.write(0x55);
        bytes.write(prefix);
        bytes.writeBytes(payload);
        return new NdefMessage(bytes.toByteArray());
    }
}
