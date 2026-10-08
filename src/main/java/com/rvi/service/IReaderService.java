package com.rvi.service;

import com.rvi.domain.ICardStatus;
import com.rvi.domain.ILinkValidation;
import com.rvi.exception.ReaderException;
import java.util.List;

/** Application operations exposed to the presentation layer, without PC/SC types. */
public interface IReaderService
{
    List<String> readers() throws ReaderException;
    ICardStatus status(String reader) throws ReaderException;
    ILinkValidation validateLinks(List<String> urls);
    List<String> read(String reader) throws ReaderException;
    void write(String reader, List<String> urls) throws ReaderException;
}
