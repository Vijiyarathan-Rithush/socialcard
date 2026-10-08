package com.rvi.service;

import com.rvi.application.IReaderRepository;
import com.rvi.domain.ICardStatus;
import com.rvi.domain.ILinkValidation;
import com.rvi.domain.NdefMessage;
import com.rvi.domain.Type2Tlv;
import com.rvi.exception.InvalidLinkException;
import com.rvi.exception.ReaderException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class ReaderServiceTest
{
    private final FakeRepository repository = new FakeRepository();
    private final NdefUriConverter converter = new NdefUriConverter();
    private final IReaderService service = new ReaderService(repository, converter);

    @Test
    void validatesWithoutAccessingHardware()
    {
        final var urls = List.of("https://example.com/profile");
        final var result = assertInstanceOf(ILinkValidation.Valid.class, service.validateLinks(urls));
        assertEquals(Type2Tlv.paddedEncodedSize(converter.encode(urls).size()), result.requiredBytes());
        assertEquals(0, repository.calls);
        assertInstanceOf(ILinkValidation.Invalid.class, service.validateLinks(List.of("http://example.com")));
        assertEquals(0, repository.calls);
    }

    @Test
    void invalidLinksCannotReachTheWriter()
    {
        assertThrows(InvalidLinkException.class, () -> service.write("reader", List.of("invalid")));
        assertEquals(0, repository.calls);
        assertNull(repository.message);
    }

    @Test
    void oversizedInputProducesAValidationResultInsteadOfEscapingIntoTheUi()
    {
        assertInstanceOf(ILinkValidation.Invalid.class,
                service.validateLinks(List.of("https://example.com/" + "x".repeat(66_000))));
        assertEquals(0, repository.calls);
    }

    @Test
    void usesInjectedRepositoryForReadAndWrite() throws Exception
    {
        final var urls = List.of("https://example.com", "https://www.example.org");
        service.write("reader", urls);
        assertEquals(converter.encode(urls), repository.message);
        assertEquals(urls, service.read("reader"));
        assertEquals(2, repository.calls);
    }

    @Test
    void hardwareFailureRetainsItsTypeAndCause()
    {
        final var expected = new ReaderException(ReaderException.Reason.MISSING_READER, "Reader unavailable.");
        repository.failure = expected;
        assertSame(expected, assertThrows(ReaderException.class, () -> service.read("missing")));
    }

    private static final class FakeRepository implements IReaderRepository
    {
        private int calls;
        private NdefMessage message;
        private ReaderException failure;

        @Override public List<String> readers() { calls++; return List.of("reader"); }
        @Override public ICardStatus status(String reader) { calls++; return new ICardStatus.NoCard(); }

        @Override
        public NdefMessage read(String reader) throws ReaderException
        {
            calls++;
            if (failure != null)
            {
                throw failure;
            }
            return message;
        }

        @Override
        public void write(String reader, NdefMessage message)
        {
            calls++;
            this.message = message;
        }
    }
}
