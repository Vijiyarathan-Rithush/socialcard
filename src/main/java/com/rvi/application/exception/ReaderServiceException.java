package com.rvi.application.exception;

public class ReaderServiceException extends RuntimeException
{
    public ReaderServiceException(String message)
    {
        super(message);
    }

    public ReaderServiceException(final String message, final Throwable cause)
    {
        super(message, cause);
    }
}
