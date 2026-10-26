package com.rvi.application;

@FunctionalInterface
public interface IReaderStatusListener
{
    void onStatusChanged(ReaderStatus status);
}
