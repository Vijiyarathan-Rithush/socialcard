package com.rvi.exception;

public final class InvalidNdefException extends IllegalArgumentException
{
    public InvalidNdefException(final String message)
    {
        super(message);
    }

    public InvalidNdefException(final String message, final Throwable cause)
    {
        super(message, cause);
    }
}
