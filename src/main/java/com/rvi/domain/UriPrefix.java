package com.rvi.domain;

import com.rvi.exception.InvalidNdefException;

/** URI identifier codes that can represent links supported by this application. */
public enum UriPrefix
{
    NONE(0x00, ""),
    HTTPS_WWW(0x02, "https://www."),
    HTTPS(0x04, "https://");

    private final int code;
    private final String value;

    UriPrefix(final int code, final String value)
    {
        this.code = code;
        this.value = value;
    }

    public int code()
    {
        return code;
    }

    public String value()
    {
        return value;
    }

    public static UriPrefix fromCode(final int code)
    {
        for (final UriPrefix prefix : values())
        {
            if (prefix.code == code)
            {
                return prefix;
            }
        }
        throw new InvalidNdefException("Unsupported URI prefix code: " + code + ". Only HTTPS links are supported.");
    }

    public static UriPrefix forUri(final String uri)
    {
        return uri.startsWith(HTTPS_WWW.value) ? HTTPS_WWW : HTTPS;
    }
}
