package com.rvi.application;

import com.rvi.domain.NDefMessage;
import javax.smartcardio.CardException;

public interface IReaderRepository
{
    NDefMessage read() throws CardException;
    void write(NDefMessage message) throws CardException;
}
