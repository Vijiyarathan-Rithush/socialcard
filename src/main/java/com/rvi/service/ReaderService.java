package com.rvi.service;

import com.rvi.application.IReaderRepository;
import com.rvi.domain.NDefMessage;
import com.rvi.service.exception.ReaderServiceException;

import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public final class ReaderService implements IReaderService
{
    private final IReaderRepository repository;

    public ReaderService(final IReaderRepository repository)
    {
        this.repository = repository;
    }

    @Override
    public NDefMessage encodeURI(final String message)
    {
        return repository.encodeURI(message);
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
        return repository.encodeURIs(urls);
    }
}
