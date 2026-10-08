package com.rvi.exception;

/** A failure at the reader boundary, without a dependency on the PC/SC API. */
public final class ReaderException extends Exception
{
    public enum Reason
    {
        IO, UNSUPPORTED, ACCESS_DENIED, VERIFICATION_FAILED, MISSING_READER,
        MISSING_CARD, INVALID_DATA, CAPACITY_EXCEEDED
    }

    private final Reason reason;

    public ReaderException(final Reason reason, final String message)
    {
        super(message);
        this.reason = java.util.Objects.requireNonNull(reason);
    }

    public ReaderException(final Reason reason, final String message, final Throwable cause)
    {
        super(message, cause);
        this.reason = java.util.Objects.requireNonNull(reason);
    }

    public Reason reason()
    {
        return reason;
    }
}
