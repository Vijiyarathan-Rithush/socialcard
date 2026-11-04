package com.rvi.application.exception;

public final class CardCapacityException extends ReaderServiceException
{
    public CardCapacityException(final int requiredBytes, final int availableBytes)
    {
        super("Message requires %d bytes, but only %d bytes are available".formatted(requiredBytes, availableBytes));
    }
}
