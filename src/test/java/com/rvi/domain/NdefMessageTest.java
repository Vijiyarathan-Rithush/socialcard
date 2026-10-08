package com.rvi.domain;

import com.rvi.exception.InvalidNdefException;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NdefMessageTest
{
    @Test
    void copiesInputAndOutputArrays()
    {
        final byte[] input = {1, 2, 3};
        final NdefMessage message = new NdefMessage(input);
        input[0] = 9;
        final byte[] output = message.data();
        output[1] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, message.data());
        assertEquals(3, message.size());
    }

    @Test
    void comparesAndHashesByContent()
    {
        final NdefMessage first = new NdefMessage(new byte[] {1, 2, 3});
        final NdefMessage second = new NdefMessage(new byte[] {1, 2, 3});
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, new NdefMessage(new byte[] {1, 2}));
        assertNotEquals(first, null);
        assertNotEquals(first, "message");
        final var messages = new HashSet<NdefMessage>();
        messages.add(first);
        messages.add(second);
        assertEquals(1, messages.size());
    }

    @Test
    void acceptsEmptyDataForAnInitializedTag()
    {
        assertEquals(0, new NdefMessage(new byte[0]).size());
    }

    @Test
    void rejectsNullData()
    {
        assertThrows(InvalidNdefException.class, () -> new NdefMessage(null));
    }
}
