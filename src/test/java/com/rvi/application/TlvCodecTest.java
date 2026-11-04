package com.rvi.application;

import com.rvi.application.exception.TlvException;
import com.rvi.domain.NDefMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

final class TlvCodecTest
{
    private final TlvCodec codec = new TlvCodec();
    private final NDefEncoder encoder = new NDefEncoder();

    @ParameterizedTest
    @CsvSource({"248,253,3", "249,254,3", "250,255,5", "251,256,5"})
    void selectsTlvLengthIndependentlyOfTheShortRecordFlag(final int suffixLength, final int ndefLength, final int overhead)
    {
        final NDefMessage message = encoder.encodeURI("https://a.ch/" + "x".repeat(suffixLength - 5));
        final byte[] encoded = codec.encode(message);

        assertEquals(ndefLength, message.data().length);
        assertEquals(ndefLength + overhead, encoded.length);
        assertEquals(0xD1, Byte.toUnsignedInt(message.data()[0]));
        assertEquals(3, encoded[0]);
        assertEquals((byte) 0xFE, encoded[encoded.length - 1]);
        assertEquals(encoded.length, codec.encodedLength(Arrays.copyOf(encoded, 4)));
        assertArrayEquals(message.data(), codec.decode(encoded).data());

        if (overhead == 5)
        {
            assertEquals(0xFF, Byte.toUnsignedInt(encoded[1]));
            assertEquals(ndefLength, Short.toUnsignedInt(ByteBuffer.wrap(encoded, 2, 2).getShort()));
        }
        else
        {
            assertEquals(ndefLength, Byte.toUnsignedInt(encoded[1]));
        }
    }

    @Test
    void roundTripsLongRecordsAndIgnoresPagePadding()
    {
        final NDefMessage message = encoder.encodeURI("https://a.ch/" + "x".repeat(300));
        final byte[] encoded = codec.encode(message);
        final byte[] padded = Arrays.copyOf(encoded, encoded.length + 3);

        assertArrayEquals(message.data(), codec.decode(padded).data());
    }

    @Test
    void rejectsEveryTruncatedTlvPrefix()
    {
        final byte[] encoded = codec.encode(encoder.encodeURI("https://a.ch/" + "x".repeat(300)));

        for (int length = 0; length < encoded.length; length++)
        {
            final byte[] truncated = Arrays.copyOf(encoded, length);
            assertThrows(TlvException.class, () -> codec.decode(truncated));
        }
    }

    @Test
    void rejectsMissingInputsAndEmptyTlv()
    {
        assertThrows(TlvException.class, () -> codec.encode(null));
        assertThrows(TlvException.class, () -> codec.decode(null));
        assertThrows(TlvException.class, () -> codec.decode(new byte[] {3, 0, (byte) 0xFE}));
    }

    @Test
    void rejectsInvalidHeadersAndMissingTerminator()
    {
        assertThrows(TlvException.class, () -> codec.encodedLength(new byte[] {0, 1}));
        assertThrows(TlvException.class, () -> codec.encodedLength(new byte[] {3, (byte) 0xFF, 0, (byte) 0xFE}));
        assertThrows(TlvException.class, () -> codec.encodedLength(new byte[] {3, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}));

        final byte[] encoded = codec.encode(encoder.encodeURI("https://a.ch"));
        encoded[encoded.length - 1] = 0;

        assertThrows(TlvException.class, () -> codec.decode(encoded));
    }

    @Test
    void acceptsTheMaximumExtendedLengthAndRejectsTheReservedLength()
    {
        final NDefMessage maximum = encoder.encodeURI("https://a.ch/" + "x".repeat(65521));
        final NDefMessage oversized = encoder.encodeURI("https://a.ch/" + "x".repeat(65522));

        assertEquals(0xFFFE, maximum.data().length);
        assertArrayEquals(maximum.data(), codec.decode(codec.encode(maximum)).data());
        assertThrows(TlvException.class, () -> codec.encode(oversized));
    }
}
