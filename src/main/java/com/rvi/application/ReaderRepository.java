package com.rvi.application;

import com.rvi.domain.NDefMessage;
import com.rvi.service.ReaderService;
import com.rvi.service.exception.ReaderServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public final class ReaderRepository implements IReaderRepository
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ReaderRepository.class);
    private final CardChannel cardChannel;
    private static final int URL_LENGTH = 249;
    private static final byte NDEF_TLV = 0x03;
    private static final byte TERMINATOR = (byte) 0xFE;
    private static final byte RECORD_HEADER = (byte) 0xD1;
    private static final byte TYPE_LENGTH = 0x01;
    private static final byte URI_TYPE = 0x55;
    private static final byte HTTPS_PREFIX = 0x04;
    private static final byte CLA = (byte) 0xFF;
    private static final byte P1 = 0x00;

    public ReaderRepository(final CardChannel cardChannel)
    {
        this.cardChannel = cardChannel;
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
            final int payloadLength = urlBytes.length + 1;

            if (records.size() + payloadLength + 4 > 254)
            {
                throw new ReaderServiceException("Combined NDEF message exceeds 254 bytes");
            }

            records.write(getRecordHeader(index, urls.size()));
            records.write(0x01);
            records.write(payloadLength);
            records.write(0x55);
            records.write(0x04);
            records.writeBytes(urlBytes);
        }

        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        output.write(0x03);
        output.write(records.size());
        output.writeBytes(records.toByteArray());
        output.write(0xFE);

        return new NDefMessage(output.toByteArray());
    }

    private static int getRecordHeader(
            final int index,
            final int recordCount)
    {
        if (recordCount == 1)
        {
            return 0xD1;
        }

        if (index == 0)
        {
            return 0x91;
        }

        if (index == recordCount - 1)
        {
            return 0x51;
        }

        return 0x11;
    }

    private static byte[] getUrlBytes(final URI uri)
    {
        if(!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
        {
            throw new ReaderServiceException("Expected a valid HTTPS URL");
        }

        final String asciiUrl = uri.toASCIIString();
        final String rest = asciiUrl.substring("https://".length());
        final byte[] urlBytes = rest.getBytes(StandardCharsets.UTF_8);

        if (urlBytes.length > URL_LENGTH)
        {
            throw new ReaderServiceException("URL is too long");
        }

        return urlBytes;
    }

    @Override
    public NDefMessage read() throws CardException
    {
        final byte[] firstPage = readPage(4);
        final int length = Byte.toUnsignedInt(firstPage[1]);

        if (firstPage[0] != NDEF_TLV || length == 0 || length == 255)
        {
            throw new CardException("Unsupported or empty NDEF message");
        }

        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        output.writeBytes(firstPage);

        final int totalLength = length + 3;

        for (int page = 5; output.size() < totalLength; page++)
        {
            output.writeBytes(readPage(page));
        }

        return new NDefMessage(Arrays.copyOf(output.toByteArray(), totalLength));
    }

    @Override
    public void write(final NDefMessage message) throws CardException
    {
        final byte[] data = message.data();
        final int paddedLength = ((data.length + 3) / 4) * 4;
        final byte[] padded = Arrays.copyOf(data, paddedLength);

        writePage(4, new byte[] {NDEF_TLV, 0x00, TERMINATOR, 0x00});

        for (int offset = 4; offset < padded.length; offset += 4)
        {
            writePage(4 + offset / 4,Arrays.copyOfRange(padded, offset, offset + 4));
        }

        writePage(4, Arrays.copyOfRange(padded, 0, 4));

        if (!Arrays.equals(data, read().data()))
        {
            throw new CardException("Verification failed");
        }
    }

    private byte[] readPage(final int page) throws CardException
    {
        final CommandAPDU command = new CommandAPDU(CLA, 0xB0, P1, page, 4);

        final ResponseAPDU response = cardChannel.transmit(command);

        if (response.getSW() != 0x9000)
        {
            throw new CardException(String.format("Reading page %d failed: %04X",page, response.getSW()));
        }

        final byte[] data = response.getData();

        if (data.length != 4)
        {
            throw new CardException("Expected four bytes");
        }

        return data;
    }

    private void writePage(final int page, final byte[] data) throws CardException
    {
        if (page < 4 || page > 129)
        {
            throw new IllegalArgumentException("NTAG215 user pages range from 4 to 129");
        }

        if (data == null || data.length != 4)
        {
            throw new IllegalArgumentException("Expected four bytes");
        }

        final CommandAPDU command = new CommandAPDU(CLA, 0xD6, P1, page, data);
        final ResponseAPDU response = cardChannel.transmit(command);

        if (response.getSW() != 0x9000)
        {
            throw new CardException(String.format("Writing page %d failed: %04X",page, response.getSW()));
        }
    }
}
