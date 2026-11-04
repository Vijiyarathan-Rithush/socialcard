package com.rvi.domain.exception;

public final class NDefValueException extends RuntimeException
{
    public NDefValueException(final String message)
    {
        super(message);
    }

    public NDefValueException(final String message, final Throwable cause)
    {
        super(message, cause);
    }
}
