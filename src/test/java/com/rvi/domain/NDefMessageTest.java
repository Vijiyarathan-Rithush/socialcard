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
                3, 9, (byte) 0xD1, 1, 5, 0x55, 4,
                0x61, 0x2E, 0x63, 0x68, (byte) 0xFE
        };
    }

    private byte[] multipleMessages()
    {
        return new byte[] {
                3, 18,
                (byte) 0x91, 1, 5, 0x55, 4, 0x61, 0x2E, 0x63, 0x68,
                0x51, 1, 5, 0x55, 4, 0x62, 0x2E, 0x63, 0x68,
                (byte) 0xFE
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
    @CsvSource({
            "0,0",
            "1,8",
            "1,10",
            "1,255",
            "2,81",
            "2,145",
            "2,241",
            "2,193",
            "2,217",
            "2,210",
            "3,0",
            "3,2",
            "4,0",
            "4,6",
            "4,255",
            "5,84",
            "6,3",
            "11,0"
    })
    void rejectsCorruptedFields(int offset, int value)
    {
        byte[] data = singleMessage();
        data[offset] = (byte) value;

        assertThrows(NDefMessageException.class, () -> new NDefMessage(data));
    }

    @ParameterizedTest
    @CsvSource({"2,209", "11,209", "11,17"})
    void rejectsInvalidRecordFlags(int offset, int header)
    {
        byte[] data = multipleMessages();
        data[offset] = (byte) header;

        assertThrows(NDefMessageException.class, () -> new NDefMessage(data));
    }

    @Test
    void rejectsIncompleteLastRecord()
    {
        byte[] data = {
                3, 11,
                (byte) 0x91, 1, 5, 0x55, 4, 0x61, 0x2E, 0x63, 0x68,
                0x51, 1, (byte) 0xFE
        };

        assertThrows(NDefMessageException.class, () -> new NDefMessage(data));
    }

    @Test
    void validatesExtendedMessagesAndEveryTruncatedPrefix()
    {
        byte[] valid = new com.rvi.application.NDefEncoder().encodeURI("https://a.ch/" + "x".repeat(478)).data();
        assertArrayEquals(valid, new NDefMessage(valid).data());
        for (int length = 0; length < valid.length; length++)
        {
            byte[] truncated = Arrays.copyOf(valid, length);
            assertThrows(NDefMessageException.class, () -> new NDefMessage(truncated));
        }
        for (int offset : new int[] {2, 3, 6, 7, 8, 9})
        {
            byte[] corrupted = valid.clone();
            corrupted[offset] = (byte) 255;
            assertThrows(NDefMessageException.class, () -> new NDefMessage(corrupted));
        }
        assertThrows(NDefMessageException.class, () -> new NDefMessage(Arrays.copyOf(valid, 497)));
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

            data[0] = 3;
            data[1] = (byte) (data.length - 3);
            data[2] = 0;
            data[data.length - 1] = (byte) 0xFE;

            assertThrows(NDefMessageException.class, () -> new NDefMessage(data));
        }
    }
}