package com.rvi.application;

import com.rvi.domain.NDefMessage;
import com.rvi.application.exception.CardCapacityException;
import com.rvi.application.exception.ReaderServiceException;

import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public final class ReaderRepository implements IReaderRepository
{


    private static final int NDEF_TLV = 0x03;
    private static final int TERMINATOR = 0xFE;

    private static final int EMPTY_NDEF_LENGTH = 0x00;
    private static final byte PADDING_BYTE = 0x00;

    private static final int PAGE_SIZE_BYTES = 4;
    private static final int FIRST_USER_PAGE = 4;
    private static final int LAST_USER_PAGE = 129;
    private static final int USER_MEMORY_BYTES = (LAST_USER_PAGE - FIRST_USER_PAGE + 1) * PAGE_SIZE_BYTES;

    private static final int APDU_CLASS = 0xFF;
    private static final int READ_BINARY_INSTRUCTION = 0xB0;
    private static final int UPDATE_BINARY_INSTRUCTION = 0xD6;
    private static final int APDU_PARAMETER_ONE = 0x00;
    private static final int SUCCESS_STATUS = 0x9000;

    private final CardChannel cardChannel;
    private final TlvCodec tlvCodec = new TlvCodec();

    public ReaderRepository(final CardChannel cardChannel)
    {
        if (cardChannel == null)
        {
            throw new ReaderServiceException("Card channel is missing");
        }

        this.cardChannel = cardChannel;
    }



    @Override
    public NDefMessage read() throws CardException
    {
        final int capacity = readNdefCapacity(false);
        final byte[] firstPage = readPage(FIRST_USER_PAGE);
        final int totalLength = tlvCodec.encodedLength(firstPage);
        final int readLength = paddedLength(totalLength, capacity);
        final ByteArrayOutputStream output = new ByteArrayOutputStream(readLength);
        output.writeBytes(firstPage);

        for (int offset = PAGE_SIZE_BYTES; offset < readLength; offset += PAGE_SIZE_BYTES)
        {
            final int page = FIRST_USER_PAGE + offset / PAGE_SIZE_BYTES;
            output.writeBytes(readPage(page));
        }

        return tlvCodec.decode(output.toByteArray());
    }

    @Override
    public void write(final NDefMessage message) throws CardException
    {
        if (message == null)
        {
            throw new ReaderServiceException("NDEF message is missing");
        }

        final byte[] data = tlvCodec.encode(message);
        final int capacity = readNdefCapacity(true);
        final int paddedLength = paddedLength(data.length, capacity);
        final byte[] padded = Arrays.copyOf(data, paddedLength);

        writePage(FIRST_USER_PAGE, new byte[] {(byte) NDEF_TLV, (byte) EMPTY_NDEF_LENGTH, (byte) TERMINATOR, PADDING_BYTE});

        for (int offset = PAGE_SIZE_BYTES; offset < padded.length; offset += PAGE_SIZE_BYTES)
        {
            final int page = FIRST_USER_PAGE + offset / PAGE_SIZE_BYTES;
            final byte[] pageData = Arrays.copyOfRange(padded, offset, offset + PAGE_SIZE_BYTES);

            writePage(page, pageData);
        }

        writePage(FIRST_USER_PAGE, Arrays.copyOfRange(padded, 0, PAGE_SIZE_BYTES));

        if (!Arrays.equals(message.data(), read().data()))
        {
            throw new ReaderServiceException("Verification failed");
        }
    }

    private int readNdefCapacity(final boolean writing)
    {
        final CommandAPDU command = new CommandAPDU(APDU_CLASS, READ_BINARY_INSTRUCTION, APDU_PARAMETER_ONE, 3, PAGE_SIZE_BYTES);
        final ResponseAPDU response = transmit(command);
        final byte[] data = response.getData();

        if (response.getSW() != SUCCESS_STATUS || data.length != PAGE_SIZE_BYTES)
        {
            throw new ReaderServiceException("Reading the capability container failed");
        }

        final int magic = Byte.toUnsignedInt(data[0]);
        final int version = Byte.toUnsignedInt(data[1]);
        final int access = Byte.toUnsignedInt(data[3]);
        final int capacity = Byte.toUnsignedInt(data[2]) * 8;

        if (magic != 0xE1 || (version >>> 4) != 1)
        {
            throw new ReaderServiceException("Unsupported capability container");
        }

        if ((access >>> 4) != 0)
        {
            throw new ReaderServiceException("Unrestricted NDEF reading is unavailable");
        }

        if (writing && (access & 0x0F) != 0)
        {
            throw new ReaderServiceException("Unrestricted NDEF writing is unavailable");
        }

        if (capacity == 0 || capacity > USER_MEMORY_BYTES)
        {
            throw new ReaderServiceException("NDEF capacity exceeds the supported NTAG215 memory layout");
        }

        return capacity;
    }

    private int paddedLength(final int dataLength, final int capacity)
    {
        final int paddedLength = ((dataLength + PAGE_SIZE_BYTES - 1) / PAGE_SIZE_BYTES) * PAGE_SIZE_BYTES;

        if (paddedLength > capacity)
        {
            throw new CardCapacityException(paddedLength, capacity);
        }

        return paddedLength;
    }

    private ResponseAPDU transmit(final CommandAPDU command)
    {
        try
        {
            return cardChannel.transmit(command);
        }
        catch (CardException | IllegalStateException exception)
        {
            throw new ReaderServiceException("Communication with the NFC reader failed", exception);
        }
    }

    private byte[] readPage(final int page) throws CardException
    {
        validatePage(page);

        final CommandAPDU command = new CommandAPDU(APDU_CLASS, READ_BINARY_INSTRUCTION, APDU_PARAMETER_ONE, page, PAGE_SIZE_BYTES);
        final ResponseAPDU response = transmit(command);

        if (response.getSW() != SUCCESS_STATUS)
        {
            throw new ReaderServiceException("Reading page %d failed: %04X".formatted(page, response.getSW()));
        }

        final byte[] data = response.getData();

        if (data.length != PAGE_SIZE_BYTES)
        {
            throw new ReaderServiceException("Expected " + PAGE_SIZE_BYTES + " bytes");
        }

        return data;
    }

    private void writePage(final int page, final byte[] data) throws CardException
    {
        validatePage(page);

        if (data == null || data.length != PAGE_SIZE_BYTES)
        {
            throw new ReaderServiceException("Expected " + PAGE_SIZE_BYTES + " bytes");
        }

        final CommandAPDU command = new CommandAPDU(APDU_CLASS, UPDATE_BINARY_INSTRUCTION, APDU_PARAMETER_ONE, page, data);
        final ResponseAPDU response = transmit(command);

        if (response.getSW() != SUCCESS_STATUS)
        {
            throw new ReaderServiceException("Writing page %d failed: %04X".formatted(page, response.getSW()));
        }
    }

    private static void validatePage(final int page)
    {
        if (page < FIRST_USER_PAGE || page > LAST_USER_PAGE)
        {
            throw new ReaderServiceException("NTAG215 user pages range from " + FIRST_USER_PAGE + " to " + LAST_USER_PAGE);
        }
    }
}
