package com.rvi.application;

import com.rvi.domain.ICardStatus;
import com.rvi.domain.NdefMessage;
import com.rvi.exception.ReaderException;
import java.util.List;

/** Hardware port. Messages contain NDEF records, without a tag-specific TLV envelope. */
public interface IReaderRepository
{
    List<String> readers() throws ReaderException;

    ICardStatus status(String reader) throws ReaderException;

    NdefMessage read(String reader) throws ReaderException;

    void write(String reader, NdefMessage message) throws ReaderException;
}
