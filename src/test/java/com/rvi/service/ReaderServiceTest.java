package com.rvi.service;

import com.rvi.service.ReaderService;
import com.rvi.application.IReaderRepository;
import com.rvi.application.exception.NDefEncodingException;
import com.rvi.domain.NDefMessage;
import org.junit.jupiter.api.Test;

import javax.smartcardio.CardException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReaderServiceTest
{
    private final StubRepository repository = new StubRepository();
    private final ReaderService service = new ReaderService(repository);

    private NDefMessage message()
    {
        return new NDefMessage(new byte[] {
                3, 9, (byte) 0xD1, 1, 5, 0x55, 4,
                0x61, 0x2E, 0x63, 0x68, (byte) 0xFE
        });
    }

    @Test
    void rejectsMissingRepository()
    {
        assertThrows(NullPointerException.class, () -> new ReaderService(null));
    }

    @Test
    void encodesSingleUrlWithoutHardware()
    {
        String url = "https://a.ch";
        byte[] expected = message().data();

        byte[] actual = service.encodeURI(url).data();

        assertArrayEquals(expected, actual);
        assertEquals(0, repository.reads);
        assertEquals(0, repository.writes);
    }

    @Test
    void encodesMultipleUrlsWithoutHardware()
    {
        List<String> urls = List.of("https://a.ch", "https://b.ch");

        byte[] actual = service.encodeURIs(urls).data();
        int recordLength = Byte.toUnsignedInt(actual[1]);
        int firstHeader = Byte.toUnsignedInt(actual[2]);
        int lastHeader = Byte.toUnsignedInt(actual[11]);

        assertEquals(18, recordLength);
        assertEquals(0x91, firstHeader);
        assertEquals(0x51, lastHeader);
        assertEquals(0, repository.reads);
        assertEquals(0, repository.writes);
    }

    @Test
    void rejectsInvalidInputWithoutHardware()
    {
        String invalidUrl = "http://a.ch";
        List<String> emptyUrls = List.of();

        assertThrows(NDefEncodingException.class, () -> service.encodeURI(invalidUrl));
        assertThrows(NDefEncodingException.class, () -> service.encodeURIs(emptyUrls));
        assertEquals(0, repository.reads);
        assertEquals(0, repository.writes);
    }

    @Test
    void readsExactlyOnce() throws CardException
    {
        NDefMessage expected = message();
        repository.result = expected;

        NDefMessage actual = service.read();

        assertSame(expected, actual);
        assertEquals(1, repository.reads);
        assertEquals(0, repository.writes);
    }

    @Test
    void writesOriginalMessageExactlyOnce() throws CardException
    {
        NDefMessage input = message();

        service.write(input);

        assertSame(input, repository.written);
        assertEquals(1, repository.writes);
        assertEquals(0, repository.reads);
    }

    @Test
    void propagatesReadFailureWithoutRetry()
    {
        CardException expected = new CardException("Reader disconnected");
        repository.failure = expected;

        CardException actual = assertThrows(CardException.class, service::read);

        assertSame(expected, actual);
        assertEquals(1, repository.reads);
    }

    @Test
    void propagatesWriteFailureWithoutRetry()
    {
        NDefMessage input = message();
        CardException expected = new CardException("Card removed");
        repository.failure = expected;

        CardException actual = assertThrows(CardException.class, () -> service.write(input));

        assertSame(expected, actual);
        assertEquals(1, repository.writes);
        assertEquals(0, repository.reads);
    }

    private static class StubRepository implements IReaderRepository
    {
        private int reads;
        private int writes;
        private NDefMessage result;
        private NDefMessage written;
        private CardException failure;

        @Override
        public NDefMessage read() throws CardException
        {
            reads++;

            if (failure != null)
            {
                throw failure;
            }

            return result;
        }

        @Override
        public void write(NDefMessage message) throws CardException
        {
            writes++;

            if (failure != null)
            {
                throw failure;
            }

            written = message;
        }
    }
}