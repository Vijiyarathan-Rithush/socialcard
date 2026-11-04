package com.rvi.domain;

import com.rvi.domain.exception.NDefValueException;

import java.util.IllformedLocaleException;
import java.util.Locale;

public record NDefText(String text, String language) implements NDefContent
{
    public NDefText
    {
        if (text == null || text.isEmpty())
        {
            throw new NDefValueException("Text is missing or empty");
        }

        if (language == null || language.isEmpty() || language.length() > 63 || !language.matches("[A-Za-z0-9-]+"))
        {
            throw new NDefValueException("Expected an ASCII language tag of at most 63 bytes");
        }

        try
        {
            new Locale.Builder().setLanguageTag(language).build();
        }
        catch (IllformedLocaleException exception)
        {
            throw new NDefValueException("Invalid language tag", exception);
        }
    }
}
