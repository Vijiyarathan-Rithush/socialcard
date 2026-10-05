package com.rvi.application;

import com.rvi.domain.NDefMessage;

import javax.smartcardio.CardException;
import java.util.List;

public interface IReaderRepository
{
    NDefMessage encodeURI(String message);
    NDefMessage read() throws CardException;
    public void write(NDefMessage message) throws CardException;
    public NDefMessage encodeURIs(final List<String> urls);
}
