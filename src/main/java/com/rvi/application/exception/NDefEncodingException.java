package com.rvi.application.exception;

public class NDefEncodingException extends RuntimeException
{
    public NDefEncodingException(String message)
    {
        super(message);
    }

    public NDefEncodingException(final String message, final Throwable cause)
    {
        super(message, cause);
    }
}
