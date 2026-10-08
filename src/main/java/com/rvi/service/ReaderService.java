package com.rvi.service;

import com.rvi.application.IReaderRepository;
import com.rvi.domain.ICardStatus;
import com.rvi.domain.ILinkValidation;
import com.rvi.domain.NdefMessage;
import com.rvi.domain.Type2Tlv;
import com.rvi.exception.InvalidLinkException;
import com.rvi.exception.InvalidNdefException;
import com.rvi.exception.ReaderException;
import java.util.List;
import java.util.Objects;

public final class ReaderService implements IReaderService
{
    private final IReaderRepository repository;
    private final NdefUriConverter converter;

    public ReaderService(final IReaderRepository repository, final NdefUriConverter converter)
    {
        this.repository = Objects.requireNonNull(repository);
        this.converter = Objects.requireNonNull(converter);
    }

    @Override
    public List<String> readers() throws ReaderException
    {
        return List.copyOf(repository.readers());
    }

    @Override
    public ICardStatus status(final String reader) throws ReaderException
    {
        return repository.status(reader);
    }

    @Override
    public ILinkValidation validateLinks(final List<String> urls)
    {
        try
        {
            final NdefMessage message = converter.encode(urls);
            return new ILinkValidation.Valid(Type2Tlv.paddedEncodedSize(message.size()), message.size());
        }
        catch (InvalidLinkException | InvalidNdefException exception)
        {
            return new ILinkValidation.Invalid(exception.getMessage());
        }
    }

    @Override
    public List<String> read(final String reader) throws ReaderException
    {
        return converter.decode(repository.read(reader));
    }

    @Override
    public void write(final String reader, final List<String> urls) throws ReaderException
    {
        repository.write(reader, converter.encode(urls));
    }
}
