package com.rvi.application.creator;

import com.rvi.domain.NDefText;

public final class NDefTextCreator
{
    private String text;
    private String language = "en";

    /** Sets nonempty UTF-8 text. */
    public NDefTextCreator text(final String text)
    {
        this.text = text;
        return this;
    }

    /** Sets an ASCII BCP 47 language tag of at most 63 bytes; defaults to en. */
    public NDefTextCreator language(final String language)
    {
        this.language = language;
        return this;
    }

    /** Validates collected values and returns a new immutable value. Does not access hardware. */
    public NDefText build()
    {
        return new NDefText(text, language);
    }
}
