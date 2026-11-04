package com.rvi.domain;

import com.rvi.domain.exception.NDefMessageException;
import com.rvi.domain.NDefMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public final class NDefMessageTest
{
    private byte[] singleMessage()
    {
        return new byte[] {
                (byte) 0xD1, 1, 5, 0x55, 4,
                0x61, 0x2E, 0x63, 0x68
        };
    }

    private byte[] multipleMessages()
    {
        return new byte[] {
                (byte) 0x91, 1, 5, 0x55, 4, 0x61, 0x2E, 0x63, 0x68,
                0x51, 1, 5, 0x55, 4, 0x62, 0x2E, 0x63, 0x68
        };
    }

    @Test
    void acceptsSingleRecord()
    {
        byte[] expected = singleMessage();

        NDefMessage message = new NDefMessage(expected);

        assertArrayEquals(expected, message.data());
    }

    @Test
    void acceptsMultipleRecords()
    {
        byte[] expected = multipleMessages();

        NDefMessage message = new NDefMessage(expected);

        assertArrayEquals(expected, message.data());
    }

    @Test
    void rejectsNull()
    {
        assertThrows(NDefMessageException.class, () -> new NDefMessage(null));
    }

    @Test
    void rejectsEveryTruncatedPrefix()
    {
        byte[] validData = singleMessage();

        for (int length = 0; length < validData.length; length++)
        {
            byte[] truncatedData = Arrays.copyOf(validData, length);
            String description = "Truncated length: " + length;

            assertThrows(NDefMessageException.class, () -> new NDefMessage(truncatedData), description);
        }
    }

    @ParameterizedTest
    @CsvSource({"0,0", "0,81", "0,145", "0,241", "0,193", "0,217", "0,210", "1,0", "1,2", "2,0", "2,6", "2,255", "3,84", "4,3"})
    void rejectsCorruptedFields(int offset, int value)
    {
        byte[] data = singleMessage();
        data[offset] = (byte) value;

        assertThrows(NDefMessageException.class, () -> new NDefMessage(data));
    }

    @ParameterizedTest
    @CsvSource({"0,209", "9,209", "9,17"})
    void rejectsInvalidRecordFlags(int offset, int header)
    {
        byte[] data = multipleMessages();
        data[offset] = (byte) header;

        assertThrows(NDefMessageException.class, () -> new NDefMessage(data));
    }

    @Test
    void rejectsIncompleteLastRecord()
    {
        byte[] data = {(byte) 0x91, 1, 5, 0x55, 4, 0x61, 0x2E, 0x63, 0x68, 0x51, 1};

        assertThrows(NDefMessageException.class, () -> new NDefMessage(data));
    }

    @Test
    void rejectsUnsignedLongLengthAboveAvailableBytes()
    {
        final byte[] data = {(byte) 0xC1, 1, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x55, 4};

        assertThrows(NDefMessageException.class, () -> new NDefMessage(data));
    }

    @Test
    void rejectsEveryTruncatedLongRecordPrefix()
    {
        final byte[] data = new com.rvi.application.NDefEncoder().encodeURI("https://a.ch/" + "x".repeat(250)).data();

        for (int length = 0; length < data.length; length++)
        {
            final byte[] truncated = Arrays.copyOf(data, length);
            assertThrows(NDefMessageException.class, () -> new NDefMessage(truncated));
        }
    }

    @Test
    void protectsAgainstInputMutation()
    {
        byte[] input = singleMessage();
        byte[] expected = singleMessage();
        NDefMessage message = new NDefMessage(input);

        Arrays.fill(input, (byte) 0);

        assertArrayEquals(expected, message.data());
    }

    @Test
    void protectsAgainstOutputMutation()
    {
        NDefMessage message = new NDefMessage(singleMessage());
        byte[] expected = singleMessage();
        byte[] firstCopy = message.data();
        byte[] secondCopy = message.data();

        Arrays.fill(firstCopy, (byte) 0);

        assertNotSame(firstCopy, secondCopy);
        assertArrayEquals(expected, secondCopy);
        assertArrayEquals(expected, message.data());
    }

    @Test
    void rejectsRandomMalformedMessagesWithDomainException()
    {
        Random random = new Random(450);

        for (int iteration = 0; iteration < 200; iteration++)
        {
            byte[] data = new byte[8 + random.nextInt(250)];
            random.nextBytes(data);

            data[0] = 0;

            assertThrows(NDefMessageException.class, () -> new NDefMessage(data));
        }
    }
}