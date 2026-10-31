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
    void acceptsMaximumShortTlvLength()
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
    void switchesToExtendedTlvAt255RecordBytes()
    {
        byte[] actual = encoder.encodeURI("https://a.ch/" + "a".repeat(245)).data();
        assertEquals(260, actual.length);
        assertEquals(0xFF, Byte.toUnsignedInt(actual[1]));
        assertEquals(0, actual[2]);
        assertEquals(255, Byte.toUnsignedInt(actual[3]));
        assertEquals(0xD1, Byte.toUnsignedInt(actual[4]));
    }

    @Test
    void switchesFromShortToNormalRecordAt256PayloadBytes()
    {
        byte[] shortRecord = encoder.encodeURI("https://a.ch/" + "a".repeat(249)).data();
        assertEquals(0xD1, Byte.toUnsignedInt(shortRecord[4]));
        assertEquals(255, Byte.toUnsignedInt(shortRecord[6]));
        byte[] normal = encoder.encodeURI("https://a.ch/" + "a".repeat(250)).data();
        assertEquals(0xC1, Byte.toUnsignedInt(normal[4]));
        assertArrayEquals(new byte[] {0, 0, 1, 0}, Arrays.copyOfRange(normal, 6, 10));
        assertEquals(0x55, normal[10]);
        assertEquals(4, normal[11]);
    }

    @Test
    void fillsThe496ByteNdefAreaAndRejectsOneByteMore()
    {
        byte[] actual = encoder.encodeURI("https://a.ch/" + "a".repeat(478)).data();
        assertEquals(496, actual.length);
        assertArrayEquals(new byte[] {3, (byte) 255, 1, (byte) 235, (byte) 193, 1, 0, 0, 1, (byte) 228, 0x55, 4},
                Arrays.copyOf(actual, 12));
        assertThrows(NDefEncodingException.class,
                () -> encoder.encodeURI("https://a.ch/" + "a".repeat(479)));
    }

    @Test
    void appliesCapacityToCombinedShortAndNormalRecords()
    {
        byte[] actual = encoder.encodeURIs(List.of("https://a.ch", "https://b.ch/" + "a".repeat(469))).data();
        assertEquals(496, actual.length);
        assertEquals(0x91, Byte.toUnsignedInt(actual[4]));
        assertEquals(0x41, Byte.toUnsignedInt(actual[13]));
        assertThrows(NDefEncodingException.class, () -> encoder.encodeURIs(
                List.of("https://a.ch", "https://b.ch/" + "a".repeat(470))));
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
        String validUrl = "https://a.ch/" + "ä".repeat(79);
        String oversizedUrl = "https://a.ch/" + "ä".repeat(80);

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