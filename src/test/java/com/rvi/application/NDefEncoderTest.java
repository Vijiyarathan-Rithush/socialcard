package com.rvi.application;

import com.rvi.application.NDefEncoder;
import com.rvi.application.exception.NDefEncodingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public final class NDefEncoderTest
{
    private final NDefEncoder encoder = new NDefEncoder();

    @Test
    void encodesSingleUrl()
    {
        String url = "https://a.ch";
        byte[] expected = {(byte) 0xD1, 1, 5, 0x55, 4, 0x61, 0x2E, 0x63, 0x68};

        byte[] actual = encoder.encodeURI(url).data();

        assertArrayEquals(expected, actual);
    }

    @Test
    void encodesMultipleUrlsInOrder()
    {
        List<String> urls = List.of("https://a.ch", "https://b.ch", "https://c.ch");
        byte[] expected = {(byte) 0x91, 1, 5, 0x55, 4, 0x61, 0x2E, 0x63, 0x68, 0x11, 1, 5, 0x55, 4, 0x62, 0x2E, 0x63, 0x68, 0x51, 1, 5, 0x55, 4, 0x63, 0x2E, 0x63, 0x68};

        byte[] actual = encoder.encodeURIs(urls).data();

        assertArrayEquals(expected, actual);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://a.ch", "  https://a.ch\t", "HTTPS://a.ch"})
    void acceptsSchemeCaseAndWhitespace(String url)
    {
        byte[] expected = encoder.encodeURI("https://a.ch").data();

        byte[] actual = encoder.encodeURI(url).data();

        assertArrayEquals(expected, actual);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "\t\n",
            "http://a.ch",
            "ftp://a.ch",
            "https://",
            "a.ch",
            "https:///path",
            "https://user:pass@a.ch",
            "https://a.ch/a b",
            "https://a.ch/%ZZ"
    })
    void rejectsInvalidUrls(String url)
    {
        assertThrows(NDefEncodingException.class, () -> encoder.encodeURI(url));
    }

    @Test
    void rejectsInvalidCollections()
    {
        List<String> emptyUrls = List.of();
        List<String> urlsWithNull = Arrays.asList("https://a.ch", null);
        List<String> urlsWithHttp = List.of("https://a.ch", "http://b.ch");

        assertThrows(NDefEncodingException.class, () -> encoder.encodeURIs(null));
        assertThrows(NDefEncodingException.class, () -> encoder.encodeURIs(emptyUrls));
        assertThrows(NDefEncodingException.class, () -> encoder.encodeURIs(urlsWithNull));
        assertThrows(NDefEncodingException.class, () -> encoder.encodeURIs(urlsWithHttp));
    }

    @Test
    void selectsShortRecordAt255PayloadBytes()
    {
        final String url = "https://a.ch/" + "a".repeat(249);
        final byte[] actual = encoder.encodeURI(url).data();

        assertEquals(259, actual.length);
        assertEquals(0xD1, Byte.toUnsignedInt(actual[0]));
        assertEquals(255, Byte.toUnsignedInt(actual[2]));
    }

    @Test
    void selectsLongRecordAt256PayloadBytes()
    {
        final String url = "https://a.ch/" + "a".repeat(250);
        final byte[] actual = encoder.encodeURI(url).data();

        assertEquals(263, actual.length);
        assertEquals(0xC1, Byte.toUnsignedInt(actual[0]));
        assertEquals(256, java.nio.ByteBuffer.wrap(actual, 2, 4).getInt());
        assertEquals(0x55, Byte.toUnsignedInt(actual[6]));
        assertEquals(0x04, Byte.toUnsignedInt(actual[7]));
    }

    @Test
    void encodesMixedShortAndLongRecordsWithoutACardLimit()
    {
        final byte[] actual = encoder.encodeURIs(List.of("https://a.ch", "https://b.ch/" + "x".repeat(500))).data();

        assertEquals(0x91, Byte.toUnsignedInt(actual[0]));
        assertEquals(0x41, Byte.toUnsignedInt(actual[9]));
        assertTrue(actual.length > 504);
    }

    @Test
    void encodesUnicodePath()
    {
        String url = "https://a.ch/ä";
        String expectedSuffix = "a.ch/%C3%A4";

        byte[] actual = encoder.encodeURI(url).data();
        String actualSuffix = new String(actual, 5, actual.length - 5, StandardCharsets.US_ASCII);
        int recordLength = actual.length;

        assertEquals(expectedSuffix, actualSuffix);
        assertEquals(16, recordLength);
    }

    @Test
    void acceptsPercentEncodedPathsBeyondTheOldLimit()
    {
        String validUrl = "https://a.ch/" + "ä".repeat(40);
        String oversizedUrl = "https://a.ch/" + "ä".repeat(41);

        assertDoesNotThrow(() -> encoder.encodeURI(validUrl));
        assertDoesNotThrow(() -> encoder.encodeURI(oversizedUrl));
    }

    @Test
    void preservesPortQueryFragmentAndEscapes()
    {
        String expectedSuffix = "a.ch:8443/p%20q?x=1&y=2#part";
        String url = "https://" + expectedSuffix;

        byte[] actual = encoder.encodeURI(url).data();
        String actualSuffix = new String(actual, 5, actual.length - 5, StandardCharsets.US_ASCII);

        assertEquals(expectedSuffix, actualSuffix);
    }

    @Test
    void remainsUsableAfterFailure()
    {
        List<String> invalidUrls = List.of("https://a.ch", "invalid");
        String validUrl = "https://a.ch";

        assertThrows(NDefEncodingException.class, () -> encoder.encodeURIs(invalidUrls));

        byte[] actual = encoder.encodeURI(validUrl).data();

        assertEquals(9, actual.length);
    }
}