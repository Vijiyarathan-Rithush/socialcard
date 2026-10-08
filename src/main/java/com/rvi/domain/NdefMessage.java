package com.rvi.domain;

import com.rvi.exception.InvalidNdefException;
import java.util.Arrays;

/** Immutable NDEF record bytes; tag-specific TLV framing belongs to the reader adapter. */
public record NdefMessage(byte[] data)
{
    public NdefMessage
    {
        if (data == null)
        {
            throw new InvalidNdefException("NDEF data must not be null.");
        }
        data = data.clone();
    }

    @Override
    public byte[] data()
    {
        return data.clone();
    }

    public int size()
    {
        return data.length;
    }

    @Override
    public boolean equals(final Object other)
    {
        return other instanceof NdefMessage message && Arrays.equals(data, message.data);
    }

    @Override
    public int hashCode()
    {
        return Arrays.hashCode(data);
    }

    @Override
    public String toString()
    {
        return "NdefMessage[bytes=" + data.length + "]";
    }
}
