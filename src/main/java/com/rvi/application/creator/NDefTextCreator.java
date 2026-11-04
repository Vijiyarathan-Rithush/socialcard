package com.rvi.application.creator;

import com.rvi.domain.NDefText;

public final class NDefTextCreator
{
    private String text;
    private String language = "en";

    public NDefTextCreator text(final String text)
    {
        this.text = text;
        return this;
    }

    public NDefTextCreator language(final String language)
    {
        this.language = language;
        return this;
    }

    public NDefText build()
    {
        return new NDefText(text, language);
    }
}
