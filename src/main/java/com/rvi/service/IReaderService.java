package com.rvi.service;

import com.rvi.domain.NDefMessage;

import java.util.List;

public interface IReaderService extends AutoCloseable
{
    void connect();
    void disconnect();
    NDefMessage encodeURI(String message);
    NDefMessage encodeURIs(List<String> urls);
    NDefMessage read();
    void write(NDefMessage message);

    @Override
    void close();
}
