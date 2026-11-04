package com.rvi.application.creator;

import com.rvi.domain.NDefUri;

public final class NDefUriCreator
{
    private String uri;

    public NDefUriCreator uri(final String uri)
    {
        this.uri = uri;
        return this;
    }

    public NDefUri build()
    {
        return new NDefUri(uri);
    }
}
