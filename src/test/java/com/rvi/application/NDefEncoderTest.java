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
        byte[] expected = {
                3, 9, (byte) 0xD1, 1, 5, 0x55, 4,
                0x61, 0x2E, 0x63, 0x68, (byte) 0xFE
        };

        byte[] actual = encoder.encodeURI(url).data();

        assertArrayEquals(expected, actual);
    }

    @Test
    void encodesMultipleUrlsInOrder()
    {
        List<String> urls = List.of("https://a.ch", "https://b.ch", "https://c.ch");
        byte[] expected = {
                3, 27,
                (byte) 0x91, 1, 5, 0x55, 4, 0x61, 0x2E, 0x63, 0x68,
                0x11, 1, 5, 0x55, 4, 0x62, 0x2E, 0x63, 0x68,
                0x51, 1, 5, 0x55, 4, 0x63, 0x2E, 0x63, 0x68,
                (byte) 0xFE
        };

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
    void acceptsMaximumUrlLength()
    {
        String url = "https://a.ch/" + "a".repeat(244);

        byte[] actual = encoder.encodeURI(url).data();
        int recordLength = Byte.toUnsignedInt(actual[1]);
        int payloadLength = Byte.toUnsignedInt(actual[4]);
        byte terminator = actual[actual.length - 1];

        assertEquals(257, actual.length);
        assertEquals(254, recordLength);
        assertEquals(250, payloadLength);
        assertEquals((byte) 0xFE, terminator);
    }

    @Test
    void rejectsUrlAboveMaximumLength()
    {
        String url = "https://a.ch/" + "a".repeat(245);

        assertThrows(NDefEncodingException.class, () -> encoder.encodeURI(url));
    }

    @Test
    void acceptsMaximumCombinedLength()
    {
        String firstUrl = "https://a.ch";
        String secondUrl = "https://b.ch/" + "a".repeat(235);
        List<String> urls = List.of(firstUrl, secondUrl);

        byte[] actual = encoder.encodeURIs(urls).data();
        int recordLength = Byte.toUnsignedInt(actual[1]);

        assertEquals(254, recordLength);
    }

    @Test
    void rejectsCombinedLengthAboveMaximum()
    {
        String firstUrl = "https://a.ch";
        String secondUrl = "https://b.ch/" + "a".repeat(236);
        List<String> urls = List.of(firstUrl, secondUrl);

        assertThrows(NDefEncodingException.class, () -> encoder.encodeURIs(urls));
    }

    @Test
    void encodesUnicodePath()
    {
        String url = "https://a.ch/ä";
        String expectedSuffix = "a.ch/%C3%A4";

        byte[] actual = encoder.encodeURI(url).data();
        String actualSuffix = new String(actual, 7, actual.length - 8, StandardCharsets.US_ASCII);
        int recordLength = Byte.toUnsignedInt(actual[1]);

        assertEquals(expectedSuffix, actualSuffix);
        assertEquals(16, recordLength);
    }

    @Test
    void checksLengthAfterPercentEncoding()
    {
        String validUrl = "https://a.ch/" + "ä".repeat(40);
        String oversizedUrl = "https://a.ch/" + "ä".repeat(41);

        assertDoesNotThrow(() -> encoder.encodeURI(validUrl));
        assertThrows(NDefEncodingException.class, () -> encoder.encodeURI(oversizedUrl));
    }

    @Test
    void preservesPortQueryFragmentAndEscapes()
    {
        String expectedSuffix = "a.ch:8443/p%20q?x=1&y=2#part";
        String url = "https://" + expectedSuffix;

        byte[] actual = encoder.encodeURI(url).data();
        String actualSuffix = new String(actual, 7, actual.length - 8, StandardCharsets.US_ASCII);

        assertEquals(expectedSuffix, actualSuffix);
    }

    @Test
    void remainsUsableAfterFailure()
    {
        List<String> invalidUrls = List.of("https://a.ch", "invalid");
        String validUrl = "https://a.ch";

        assertThrows(NDefEncodingException.class, () -> encoder.encodeURIs(invalidUrls));

        byte[] actual = encoder.encodeURI(validUrl).data();

        assertEquals(12, actual.length);
    }
}