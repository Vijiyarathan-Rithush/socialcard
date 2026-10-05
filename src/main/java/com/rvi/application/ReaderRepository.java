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

public final class ReaderRepository implements IReaderRepository
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ReaderRepository.class);
    private final CardChannel cardChannel;
    private static final int URL_LENGTH = 249;

    public ReaderRepository(CardChannel cardChannel)
    {
        this.cardChannel = cardChannel;
    }

    @Override
    public NDefMessage encodeURI(String url)
    {
        LOGGER.info("Encoding URI: " + url);

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

        final int payLoadLength = urlBytes.length + 1;
        final int ndefLength = payLoadLength + 4;
        final byte[] data = new byte[ndefLength + 3];

        data[0] = 0x03;
        data[1] = (byte) ndefLength;
        data[2] = (byte) 0xD1;
        data[3] = (byte) 0x01;
        data[4] = (byte) payLoadLength;
        data[5] = 0x55;
        data[6] = 0x04;

        System.arraycopy(urlBytes, 0, data, 7, urlBytes.length);

        data[data.length - 1] = (byte) 0xFE;

        LOGGER.info("Encoding URI method finished");

        return new NDefMessage(data);
    }

    private static byte[] getUrlBytes(URI uri)
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
    public NDefMessage read() throws CardException {
        final byte[] firstPage = readPage(4);
        final int length = Byte.toUnsignedInt(firstPage[1]);

        if (firstPage[0] != 0x03 || length == 0 || length == 255) {
            throw new CardException("Unsupported or empty NDEF message");
        }

        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(firstPage);

        final int totalLength = length + 3;

        for (int page = 5; output.size() < totalLength; page++) {
            output.writeBytes(readPage(page));
        }

        return new NDefMessage(
                Arrays.copyOf(output.toByteArray(), totalLength)
        );
    }

    @Override
    public void write(NDefMessage message) throws CardException {
        final byte[] data = message.data();
        final int paddedLength = ((data.length + 3) / 4) * 4;
        final byte[] padded = Arrays.copyOf(data, paddedLength);

        writePage(4, new byte[] {0x03, 0x00, (byte) 0xFE, 0x00});

        for (int offset = 4; offset < padded.length; offset += 4) {
            writePage(
                    4 + offset / 4,
                    Arrays.copyOfRange(padded, offset, offset + 4)
            );
        }

        writePage(4, Arrays.copyOfRange(padded, 0, 4));

        if (!Arrays.equals(data, read().data())) {
            throw new CardException("Verification failed");
        }
    }

    private byte[] readPage(int page) throws CardException {
        final CommandAPDU command =
                new CommandAPDU(0xFF, 0xB0, 0x00, page, 4);

        final ResponseAPDU response = cardChannel.transmit(command);

        if (response.getSW() != 0x9000) {
            throw new CardException(
                    String.format("Reading page %d failed: %04X",
                            page, response.getSW())
            );
        }

        final byte[] data = response.getData();

        if (data.length != 4) {
            throw new CardException("Expected four bytes");
        }

        return data;
    }

    private void writePage(int page, byte[] data) throws CardException {
        if (page < 4 || page > 129) {
            throw new IllegalArgumentException(
                    "NTAG215 user pages range from 4 to 129"
            );
        }

        if (data == null || data.length != 4) {
            throw new IllegalArgumentException("Expected four bytes");
        }

        final CommandAPDU command =
                new CommandAPDU(0xFF, 0xD6, 0x00, page, data);

        final ResponseAPDU response = cardChannel.transmit(command);

        if (response.getSW() != 0x9000) {
            throw new CardException(
                    String.format("Writing page %d failed: %04X",
                            page, response.getSW())
            );
        }
    }
}
