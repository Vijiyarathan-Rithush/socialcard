package com.rvi.exception;

public final class InvalidLinkException extends IllegalArgumentException
{
    public InvalidLinkException(final String message)
    {
        super(message);
    }

    public InvalidLinkException(final String message, final Throwable cause)
    {
        super(message, cause);
    }
}
