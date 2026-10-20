package com.rvi.service;

import com.rvi.application.IReaderRepository;
import com.rvi.application.NDefEncoder;
import com.rvi.domain.NDefMessage;

import javax.smartcardio.CardException;
import java.util.List;
import java.util.Objects;

public final class ReaderService implements IReaderService
{
    private final IReaderRepository repository;
    private final NDefEncoder encoder = new NDefEncoder();

    public ReaderService(final IReaderRepository repository)
    {
        this.repository = Objects.requireNonNull(repository, "repository is null");
    }

    @Override
    public NDefMessage encodeURI(final String message)
    {
        return encoder.encodeURI(message);
    }

    @Override
    public NDefMessage read() throws CardException
    {
        return repository.read();
    }

    @Override
    public void write(final NDefMessage message) throws CardException
    {
        repository.write(message);
    }

    @Override
    public NDefMessage encodeURIs(List<String> urls)
    {
        return encoder.encodeURIs(urls);
    }
}
