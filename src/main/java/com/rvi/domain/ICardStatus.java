package com.rvi.domain;

/** Card states contain device information; presentation text belongs to the UI. */
public sealed interface ICardStatus
{
    record NoCard() implements ICardStatus {}
    record Unavailable() implements ICardStatus {}
    record Unsupported() implements ICardStatus {}

    record Ready(boolean writable, int capacity) implements ICardStatus
    {
        public Ready
        {
            if (capacity <= 0)
            {
                throw new IllegalArgumentException("Card capacity must be positive.");
            }
        }
    }
}
