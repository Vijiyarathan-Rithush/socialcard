package com.rvi.application;

import com.rvi.domain.NDefMessage;
import com.rvi.service.exception.ReaderServiceException;

import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class ReaderRepository implements IReaderRepository
{
    private static final String HTTPS_SCHEME = "https";
    private static final String HTTPS_URI_PREFIX = "https://";

    private static final int MAX_URI_SUFFIX_BYTES = 249;
    private static final int MAX_SHORT_NDEF_LENGTH = 254;

    private static final int NDEF_TLV = 0x03;
    private static final int TERMINATOR = 0xFE;
    private static final int TYPE_LENGTH = 0x01;
    private static final int URI_TYPE = 0x55;
    private static final int HTTPS_PREFIX = 0x04;

    private static final int SINGLE_RECORD_HEADER = 0xD1;
    private static final int FIRST_RECORD_HEADER = 0x91;
    private static final int LAST_RECORD_HEADER = 0x51;
    private static final int MIDDLE_RECORD_HEADER = 0x11;

    private static final int URI_PREFIX_BYTES = 1;
    private static final int URI_RECORD_OVERHEAD = 4;
    private static final int TLV_OVERHEAD = 3;
    private static final int TLV_TYPE_OFFSET = 0;
    private static final int TLV_LENGTH_OFFSET = 1;
    private static final int EXTENDED_TLV_LENGTH_MARKER = 0xFF;
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

    public ReaderRepository(final CardChannel cardChannel)
    {
        this.cardChannel = Objects.requireNonNull(cardChannel, "Card channel is missing");
    }

    @Override
    public NDefMessage encodeURI(final String url)
    {
        if (url == null)
        {
            throw new ReaderServiceException("URL is null");
        }

        return encodeURIs(List.of(url));
    }

    @Override
    public NDefMessage encodeURIs(final List<String> urls)
    {
        if (urls == null || urls.isEmpty())
        {
            throw new ReaderServiceException("URLs are missing");
        }

        final ByteArrayOutputStream records = new ByteArrayOutputStream();

        for (int index = 0; index < urls.size(); index++)
        {
            final String url = urls.get(index);

            if (url == null || url.isBlank())
            {
                throw new ReaderServiceException("URL is null or blank");
            }

            final URI uri;

            try
            {
                uri = URI.create(url.strip());
            }
            catch (IllegalArgumentException exception)
            {
                throw new ReaderServiceException("Invalid URI: " + url);
            }

            final byte[] urlBytes = getUrlBytes(uri);
            final int payloadLength = urlBytes.length + URI_PREFIX_BYTES;
            final int recordLength = payloadLength + URI_RECORD_OVERHEAD;

            if (records.size() + recordLength > MAX_SHORT_NDEF_LENGTH)
            {
                throw new ReaderServiceException("Combined NDEF message exceeds " + MAX_SHORT_NDEF_LENGTH + " bytes");
            }

            records.write(getRecordHeader(index, urls.size()));
            records.write(TYPE_LENGTH);
            records.write(payloadLength);
            records.write(URI_TYPE);
            records.write(HTTPS_PREFIX);
            records.writeBytes(urlBytes);
        }

        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        output.write(NDEF_TLV);
        output.write(records.size());
        output.writeBytes(records.toByteArray());
        output.write(TERMINATOR);

        return new NDefMessage(output.toByteArray());
    }

    private static int getRecordHeader(final int index, final int recordCount)
    {
        if (recordCount == 1)
        {
            return SINGLE_RECORD_HEADER;
        }

        if (index == 0)
        {
            return FIRST_RECORD_HEADER;
        }

        if (index == recordCount - 1)
        {
            return LAST_RECORD_HEADER;
        }

        return MIDDLE_RECORD_HEADER;
    }

    private static byte[] getUrlBytes(final URI uri)
    {
        if (!HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
        {
            throw new ReaderServiceException("Expected a valid HTTPS URL");
        }

        final String asciiUrl = uri.toASCIIString();
        final String uriSuffix = asciiUrl.substring(HTTPS_URI_PREFIX.length());
        final byte[] urlBytes = uriSuffix.getBytes(StandardCharsets.UTF_8);

        if (urlBytes.length > MAX_URI_SUFFIX_BYTES)
        {
            throw new ReaderServiceException("URL is too long");
        }

        return urlBytes;
    }

    @Override
    public NDefMessage read() throws CardException
    {
        final byte[] firstPage = readPage(FIRST_USER_PAGE);
        final int ndefLength = Byte.toUnsignedInt(firstPage[TLV_LENGTH_OFFSET]);

        if (Byte.toUnsignedInt(firstPage[TLV_TYPE_OFFSET]) != NDEF_TLV || ndefLength == EMPTY_NDEF_LENGTH || ndefLength == EXTENDED_TLV_LENGTH_MARKER)
        {
            throw new CardException("Unsupported or empty NDEF message");
        }

        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(firstPage);

        final int totalLength = ndefLength + TLV_OVERHEAD;

        for (int page = FIRST_USER_PAGE + 1; output.size() < totalLength; page++)
        {
            output.writeBytes(readPage(page));
        }

        return new NDefMessage(Arrays.copyOf(output.toByteArray(), totalLength));
    }

    @Override
    public void write(final NDefMessage message) throws CardException
    {
        Objects.requireNonNull(message, "NDEF message is missing");

        final byte[] data = message.data();
        final int paddedLength = ((data.length + PAGE_SIZE_BYTES - 1) / PAGE_SIZE_BYTES) * PAGE_SIZE_BYTES;

        if (paddedLength > USER_MEMORY_BYTES)
        {
            throw new IllegalArgumentException("Message exceeds NTAG215 user memory");
        }

        final byte[] padded = Arrays.copyOf(data, paddedLength);

        writePage(FIRST_USER_PAGE, new byte[] {(byte) NDEF_TLV, (byte) EMPTY_NDEF_LENGTH, (byte) TERMINATOR, PADDING_BYTE});

        for (int offset = PAGE_SIZE_BYTES; offset < padded.length; offset += PAGE_SIZE_BYTES)
        {
            final int page = FIRST_USER_PAGE + offset / PAGE_SIZE_BYTES;
            final byte[] pageData = Arrays.copyOfRange(padded, offset, offset + PAGE_SIZE_BYTES);

            writePage(page, pageData);
        }

        writePage(FIRST_USER_PAGE, Arrays.copyOfRange(padded, 0, PAGE_SIZE_BYTES));

        if (!Arrays.equals(data, read().data()))
        {
            throw new CardException("Verification failed");
        }
    }

    private byte[] readPage(final int page) throws CardException
    {
        validatePage(page);

        final CommandAPDU command = new CommandAPDU(APDU_CLASS, READ_BINARY_INSTRUCTION, APDU_PARAMETER_ONE, page, PAGE_SIZE_BYTES);
        final ResponseAPDU response = cardChannel.transmit(command);

        if (response.getSW() != SUCCESS_STATUS)
        {
            throw new CardException(String.format("Reading page %d failed: %04X", page, response.getSW()));
        }

        final byte[] data = response.getData();

        if (data.length != PAGE_SIZE_BYTES)
        {
            throw new CardException("Expected " + PAGE_SIZE_BYTES + " bytes");
        }

        return data;
    }

    private void writePage(final int page, final byte[] data) throws CardException
    {
        validatePage(page);

        if (data == null || data.length != PAGE_SIZE_BYTES)
        {
            throw new IllegalArgumentException("Expected " + PAGE_SIZE_BYTES + " bytes");
        }

        final CommandAPDU command = new CommandAPDU(APDU_CLASS, UPDATE_BINARY_INSTRUCTION, APDU_PARAMETER_ONE, page, data);
        final ResponseAPDU response = cardChannel.transmit(command);

        if (response.getSW() != SUCCESS_STATUS)
        {
            throw new CardException(String.format("Writing page %d failed: %04X", page, response.getSW()));
        }
    }

    private static void validatePage(final int page)
    {
        if (page < FIRST_USER_PAGE || page > LAST_USER_PAGE)
        {
            throw new IllegalArgumentException("NTAG215 user pages range from " + FIRST_USER_PAGE + " to " + LAST_USER_PAGE);
        }
    }
}