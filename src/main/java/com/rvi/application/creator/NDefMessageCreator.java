package com.rvi.application.creator;

import com.rvi.application.NDefEncoder;
import com.rvi.domain.NDefContent;
import com.rvi.domain.NDefMessage;
import com.rvi.domain.exception.NDefValueException;

import java.util.ArrayList;
import java.util.List;

public final class NDefMessageCreator
{
    private final List<NDefContent> contents = new ArrayList<>();

    /** Appends a non-null typed content value, preserving insertion order. */
    public NDefMessageCreator add(final NDefContent content)
    {
        if (content == null)
        {
            throw new NDefValueException("NDEF content is missing");
        }

        contents.add(content);
        return this;
    }

    /** Validates collected values and returns a new immutable value. Does not access hardware. */
    public NDefMessage build()
    {
        return new NDefEncoder().encode(List.copyOf(contents));
    }
}
