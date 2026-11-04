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

    public NDefMessageCreator add(final NDefContent content)
    {
        if (content == null)
        {
            throw new NDefValueException("NDEF content is missing");
        }

        contents.add(content);
        return this;
    }

    public NDefMessage build()
    {
        return new NDefEncoder().encode(List.copyOf(contents));
    }
}
