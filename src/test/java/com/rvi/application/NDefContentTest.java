package com.rvi.application;

import com.rvi.application.creator.NDefContactCreator;
import com.rvi.application.creator.NDefMessageCreator;
import com.rvi.application.creator.NDefTextCreator;
import com.rvi.application.creator.NDefUriCreator;
import com.rvi.application.creator.NDefWifiCreator;
import com.rvi.application.exception.NDefEncodingException;
import com.rvi.domain.NDefContact;
import com.rvi.domain.NDefMessage;
import com.rvi.domain.NDefText;
import com.rvi.domain.NDefUri;
import com.rvi.domain.WifiAuthentication;
import com.rvi.domain.WifiEncryption;
import com.rvi.domain.exception.NDefMessageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class NDefContentTest
{
    @Test
    void encodesUtf8TextWithALanguageStatusByte()
    {
        final NDefText text = new NDefTextCreator().text("Grüezi").language("de").build();
        final NDefMessage message = new NDefMessageCreator().add(text).build();

        assertArrayEquals(HexFormat.of().parseHex("d1010a540264654772c3bc657a69"), message.data());
    }

    @ParameterizedTest
    @CsvSource({"http://a.ch,3,a.ch", "https://a.ch,4,a.ch", "tel:+41791234567,0,tel:+41791234567", "mailto:a@b.ch,0,mailto:a@b.ch"})
    void encodesGeneralUris(final String uri, final int prefix, final String suffix)
    {
        final NDefMessage message = new NDefMessageCreator().add(new NDefUriCreator().uri(uri).build()).build();
        final EncodedContent value = unpack(message).getFirst();

        assertEquals(0x55, value.type()[0]);
        assertEquals(prefix, value.payload()[0]);
        assertEquals(suffix, new String(value.payload(), 1, value.payload().length - 1, StandardCharsets.UTF_8));
    }

    @Test
    void preservesTheOrderAndFlagsOfMixedContents()
    {
        final NDefUri uri = new NDefUriCreator().uri("https://a.ch").build();
        final NDefText text = new NDefTextCreator().text("Hallo").build();
        final NDefContact contact = new NDefContactCreator().firstName("Rithush").build();
        final NDefMessage message = new NDefMessageCreator().add(uri).add(text).add(contact).add(new NDefWifiCreator().ssid("WLAN").password("password").build()).build();
        final List<EncodedContent> values = unpack(message);

        assertEquals(List.of(0x91, 0x11, 0x12, 0x52), values.stream().map(EncodedContent::header).toList());
        assertArrayEquals(new byte[] {0x55}, values.get(0).type());
        assertArrayEquals(new byte[] {0x54}, values.get(1).type());
        assertEquals("text/vcard", new String(values.get(2).type(), StandardCharsets.US_ASCII));
        assertEquals("application/vnd.wfa.wsc", new String(values.get(3).type(), StandardCharsets.US_ASCII));
        assertArrayEquals(message.data(), new TlvDecoder().decode(new TlvEncoder().encode(message)).data());
    }

    @Test
    void encodesContactFieldsWithEscapingAndCrlf()
    {
        final NDefContact contact = new NDefContactCreator().firstName("Ri;th,ush").lastName("Test\nInjected").email("hello@rithush.dev").phone("+41791234567").website("https://rithush.dev").build();
        final byte[] payload = unpack(new NDefMessageCreator().add(contact).build()).getFirst().payload();
        final String vcard = new String(payload, StandardCharsets.UTF_8);

        assertTrue(vcard.startsWith("BEGIN:VCARD\r\nVERSION:4.0\r\n"));
        assertTrue(vcard.contains("FN:Ri;th\\,ush Test\\nInjected\r\n"));
        assertTrue(vcard.contains("N:Test\\nInjected;Ri\\;th\\,ush;;;\r\n"));
        assertTrue(vcard.contains("EMAIL:hello@rithush.dev\r\n"));
        assertTrue(vcard.contains("TEL;VALUE=text:+41791234567\r\n"));
        assertTrue(vcard.contains("URL:https://rithush.dev\r\n"));
        assertTrue(vcard.endsWith("END:VCARD\r\n"));
    }

    @Test
    void foldsLongVcardLinesAtUtf8Boundaries()
    {
        final String name = "ä😀".repeat(40);
        final NDefMessage message = new NDefMessageCreator().add(new NDefContactCreator().firstName(name).build()).build();
        final String vcard = new String(unpack(message).getFirst().payload(), StandardCharsets.UTF_8);

        assertTrue(vcard.contains("\r\n "));
        assertFalse(vcard.contains("\uFFFD"));
        assertTrue(vcard.replace("\r\n ", "").contains("FN:" + name + "\r\n"));

        for (final String line : vcard.split("\r\n"))
        {
            assertTrue(line.getBytes(StandardCharsets.UTF_8).length <= 75);
        }
    }

    @Test
    void encodesWpa2CredentialsAsNestedWscAttributes()
    {
        final NDefMessage message = new NDefMessageCreator().add(new NDefWifiCreator().ssid("WLAN").password("password").build()).build();
        final byte[] payload = unpack(message).getFirst().payload();
        final byte[] expected = HexFormat.of().parseHex("104a000110100e002f102600010110450004574c414e100300020020100f000200081027000870617373776f726410200006ffffffffffff");

        assertArrayEquals(expected, payload);
    }

    @Test
    void encodesOpenWifiWithoutAPassword()
    {
        final NDefMessage message = new NDefMessageCreator().add(new NDefWifiCreator().ssid("Guest").authentication(WifiAuthentication.OPEN).encryption(WifiEncryption.NONE).build()).build();
        final String payload = HexFormat.of().formatHex(unpack(message).getFirst().payload());

        assertTrue(payload.contains("100300020001100f0002000110270000"));
    }

    @Test
    void selectsLongTextPayloadsAndRejectsMalformedUtf8()
    {
        final NDefMessage message = new NDefMessageCreator().add(new NDefTextCreator().text("ä".repeat(200)).build()).build();

        assertEquals(0xC1, Byte.toUnsignedInt(message.data()[0]));
        assertEquals(403, unpack(message).getFirst().payload().length);
        assertThrows(NDefMessageException.class, () -> new NDefMessage(new byte[] {(byte) 0xD1, 1, 4, 0x54, 2, 'd', 'e', (byte) 0xFF}));
    }

    @Test
    void rejectsMissingEncoderValuesWithCustomExceptions()
    {
        final NDefEncoder encoder = new NDefEncoder();

        assertThrows(NDefEncodingException.class, () -> encoder.encode(null));
        assertThrows(NDefEncodingException.class, () -> encoder.encode(Arrays.asList((com.rvi.domain.NDefContent) null)));
    }

    private List<EncodedContent> unpack(final NDefMessage message)
    {
        final ByteBuffer input = ByteBuffer.wrap(message.data());
        final List<EncodedContent> values = new ArrayList<>();

        while (input.hasRemaining())
        {
            final int header = Byte.toUnsignedInt(input.get());
            final int typeLength = Byte.toUnsignedInt(input.get());
            final int payloadLength;

            if ((header & 0x10) != 0)
            {
                payloadLength = Byte.toUnsignedInt(input.get());
            }
            else
            {
                payloadLength = input.getInt();
            }

            final byte[] type = new byte[typeLength];
            final byte[] payload = new byte[payloadLength];
            input.get(type);
            input.get(payload);
            values.add(new EncodedContent(header, type, payload));
        }

        return values;
    }

    private record EncodedContent(int header, byte[] type, byte[] payload)
    {
    }
}
