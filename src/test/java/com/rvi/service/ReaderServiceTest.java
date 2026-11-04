package com.rvi.service;

import com.rvi.service.ReaderService;
import com.rvi.application.IReaderRepository;
import com.rvi.application.exception.NDefEncodingException;
import com.rvi.application.exception.ReaderServiceException;
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
        return new NDefMessage(new byte[] {(byte) 0xD1, 1, 5, 0x55, 4, 0x61, 0x2E, 0x63, 0x68});
    }

    @Test
    void supportsConvenienceWriteAndTypedReads()
    {
        service.writeURI("mailto:hello@example.com");
        repository.result = repository.written;
        assertEquals(List.of("mailto:hello@example.com"), service.readURIs());
        assertEquals(List.of(new com.rvi.domain.NDefUri("mailto:hello@example.com")), service.readContents());
        assertEquals(1, repository.writes);
        assertEquals(2, repository.reads);
    }

    @Test
    void rejectsMissingRepository()
    {
        assertThrows(ReaderServiceException.class, () -> new ReaderService(null));
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
        int recordLength = actual.length;
        int firstHeader = Byte.toUnsignedInt(actual[0]);
        int lastHeader = Byte.toUnsignedInt(actual[9]);

        assertEquals(18, recordLength);
        assertEquals(0x91, firstHeader);
        assertEquals(0x51, lastHeader);
        assertEquals(0, repository.reads);
        assertEquals(0, repository.writes);
    }

    @Test
    void rejectsInvalidInputWithoutHardware()
    {
        String invalidUrl = "a.ch";
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

        ReaderServiceException actual = assertThrows(ReaderServiceException.class, service::read);

        assertSame(expected, actual.getCause());
        assertEquals(1, repository.reads);
    }

    @Test
    void propagatesWriteFailureWithoutRetry()
    {
        NDefMessage input = message();
        CardException expected = new CardException("Card removed");
        repository.failure = expected;

        ReaderServiceException actual = assertThrows(ReaderServiceException.class, () -> service.write(input));

        assertSame(expected, actual.getCause());
        assertEquals(1, repository.writes);
        assertEquals(0, repository.reads);
    }

    @Test
    void preservesAnExternallyManagedRepository()
    {
        final NDefMessage expected = message();
        repository.result = expected;

        assertThrows(ReaderServiceException.class, service::connect);
        service.disconnect();
        service.close();

        assertSame(expected, service.read());
        assertEquals(1, repository.reads);
        assertEquals(0, repository.writes);
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