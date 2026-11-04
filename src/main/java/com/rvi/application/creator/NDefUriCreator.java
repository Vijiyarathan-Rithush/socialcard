package com.rvi.application.creator;

import com.rvi.domain.NDefUri;

public final class NDefUriCreator
{
    private String uri;

    /** Sets an absolute URI with a scheme, e.g. https://example.com or tel:+41791234567. */
    public NDefUriCreator uri(final String uri)
    {
        this.uri = uri;
        return this;
    }

    /** Validates collected values and returns a new immutable value. Does not access hardware. */
    public NDefUri build()
    {
        return new NDefUri(uri);
    }
}
