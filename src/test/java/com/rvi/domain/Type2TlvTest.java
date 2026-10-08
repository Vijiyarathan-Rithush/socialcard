package com.rvi.domain;

import com.rvi.exception.InvalidNdefException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Type2TlvTest
{
    @Test
    void supportsShortAndExtendedLengthsAtTheBoundary()
    {
        for (int size : new int[]{0, 1, 254, 255, 491})
        {
            final byte[] bytes = new byte[size];
            Arrays.fill(bytes, (byte) 0x41);
            final NdefMessage message = new NdefMessage(bytes);
            final byte[] encoded = Type2Tlv.encode(message);
            assertEquals(Type2Tlv.encodedSize(size), encoded.length);
            assertEquals(size < 255 ? size : 255, Byte.toUnsignedInt(encoded[1]));
            assertEquals(message, Type2Tlv.parse(encoded));
        }
    }

    @Test
    void skipsNullAndReservedBytesDescribedByAControlTlv()
    {
        final byte[] data = new byte[40];
        // A memory control TLV reserves physical bytes 32..35 (relative offsets 16..19).
        System.arraycopy(new byte[]{0, 2, 3, (byte) 0x80, 4, 2, 3, 20}, 0, data, 0, 8);
        final byte[] expected = new byte[20];
        int offset = 8;
        for (int index = 0; index < expected.length; index++)
        {
            expected[index] = (byte) (index + 1);
            if (offset == 16)
            {
                offset = 20;
            }
            data[offset++] = expected[index];
        }
        Arrays.fill(data, 16, 20, (byte) 0xFA);
        data[offset] = (byte) 0xFE;
        assertArrayEquals(expected, Type2Tlv.parse(data).data());
        assertTrue(Type2Tlv.inspect(data).hasAdditionalTlvs());
    }

    @Test
    void rejectsTruncationAndNonCanonicalExtendedLengths()
    {
        assertThrows(InvalidNdefException.class, () -> Type2Tlv.parse(new byte[]{3}));
        assertThrows(InvalidNdefException.class, () -> Type2Tlv.parse(new byte[]{3, 4, 1, 2}));
        assertThrows(InvalidNdefException.class, () -> Type2Tlv.parse(new byte[]{3, (byte) 255, 1}));
        assertThrows(InvalidNdefException.class, () -> Type2Tlv.parse(new byte[]{3, (byte) 255, 0, 1, 1}));
    }

    @Test
    void rejectsRetroactiveAndOverlappingReservations()
    {
        assertThrows(InvalidNdefException.class,
                () -> Type2Tlv.parse(new byte[]{2, 3, 0x40, 4, 2}));
        final byte[] overlapping = new byte[40];
        System.arraycopy(new byte[]{2, 3, (byte) 0x80, 4, 2, 2, 3, (byte) 0x80, 4, 2},
                0, overlapping, 0, 10);
        assertThrows(InvalidNdefException.class, () -> Type2Tlv.parse(overlapping));
    }

    @Test
    void rejectsLengthThatWouldReadPastReservedMemory()
    {
        final byte[] data = new byte[24];
        System.arraycopy(new byte[]{2, 3, (byte) 0x80, 4, 2, 3, 16}, 0, data, 0, 7);
        assertThrows(InvalidNdefException.class, () -> Type2Tlv.parse(data));
    }

    @Test
    void acceptsAnExactFitWithoutATerminatorAndAnInitializedEmptyTag()
    {
        assertArrayEquals(new byte[]{7, 8}, Type2Tlv.parse(new byte[]{3, 2, 7, 8}).data());
        assertEquals(0, Type2Tlv.parse(new byte[]{3, 0, (byte) 0xFE, 0}).size());
    }
}
