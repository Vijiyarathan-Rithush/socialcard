package com.rvi.application;

import com.rvi.domain.*;
import com.rvi.domain.exception.NDefMessageException;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NDefDecoderTest
{
    private final NDefEncoder encoder = new NDefEncoder();
    private final NDefDecoder decoder = new NDefDecoder();

    @Test
    void roundTripsAllContentTypesInOrderIncludingEscapingAndFolding()
    {
        final List<NDefContent> contents = List.of(
                new NDefUri("https://example.com/ä"), new NDefUri("http://example.com"),
                new NDefUri("mailto:hello@example.com"), new NDefUri("tel:+41791234567"),
                new NDefText("Grüezi 🌍".repeat(50), "de-CH"),
                new NDefContact("A;B,\\C\nD" + "ä".repeat(60), "Last;Name", "a@example.com", "+41 79 123 45 67", "https://example.com"),
                new NDefWifi("Gäste", WifiAuthentication.WPA2_PERSONAL, WifiEncryption.AES, "password123"),
                new NDefWifi("Guest", WifiAuthentication.OPEN, WifiEncryption.NONE, ""));
        final var result = decoder.decode(encoder.encode(contents));
        assertEquals(contents, result);
        assertThrows(UnsupportedOperationException.class, () -> result.clear());
    }

    @Test
    void extractsUrisFromMixedContent()
    {
        final var message = encoder.encode(List.of(new NDefText("Hello", "en"), new NDefUri("tel:+41")));
        assertEquals(List.of("tel:+41"), decoder.decodeURIs(message));
        assertTrue(decoder.decodeURIs(encoder.encode(List.of(new NDefText("Hello", "en")))).isEmpty());
    }

    @Test
    void decodesExternalUriPrefix()
    {
        final byte[] suffix = "example.com".getBytes(StandardCharsets.UTF_8);
        final byte[] record = ByteBuffer.allocate(suffix.length + 5)
                .put((byte) 0xD1).put((byte) 1).put((byte) (suffix.length + 1))
                .put((byte) 'U').put((byte) 2).put(suffix).array();
        assertEquals(List.of("https://www.example.com"), decoder.decodeURIs(new NDefMessage(record)));
    }

    @Test
    void rejectsMalformedMimePayloadsAndUnsupportedContactFields()
    {
        assertThrows(NDefMessageException.class, () -> decoder.decode(mime("application/vnd.wfa.wsc", new byte[] {1})));
        assertThrows(NDefMessageException.class, () -> decoder.decode(mime("application/vnd.wfa.wsc", new byte[] {0x10, 0x0E, 0, 2, 0})));
        assertThrows(NDefMessageException.class, () -> decoder.decode(mime("text/vcard", "not a card".getBytes(StandardCharsets.UTF_8))));
        final String external = "BEGIN:VCARD\r\nVERSION:4.0\r\nFN:Example\r\nN:Last;First;;;\r\nORG:Company\r\nEND:VCARD\r\n";
        assertThrows(NDefMessageException.class, () -> decoder.decode(mime("text/vcard", external.getBytes(StandardCharsets.UTF_8))));
        assertThrows(NullPointerException.class, () -> decoder.decode(null));
        assertThrows(NDefMessageException.class, () -> decoder.decode(mime("text/vcard", new byte[] {(byte) 0xFF})));
        assertTrue(decoder.decodeURIs(mime("text/vcard", new byte[] {(byte) 0xFF})).isEmpty());
    }

    private NDefMessage mime(final String type, final byte[] payload)
    {
        final byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        return new NDefMessage(ByteBuffer.allocate(3 + name.length + payload.length)
                .put((byte) 0xD2).put((byte) name.length).put((byte) payload.length).put(name).put(payload).array());
    }
}
