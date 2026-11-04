package com.rvi.domain;

import com.rvi.domain.exception.NDefValueException;

import java.net.URI;
import java.net.URISyntaxException;

public record NDefUri(String uri) implements NDefContent
{
    public NDefUri
    {
        if (uri == null || uri.isBlank())
        {
            throw new NDefValueException("URI is missing or blank");
        }

        try
        {
            final URI parsed = new URI(uri.strip());

            if (!parsed.isAbsolute() || parsed.getRawSchemeSpecificPart().isEmpty())
            {
                throw new NDefValueException("Expected an absolute URI");
            }

            if ("http".equalsIgnoreCase(parsed.getScheme()) || "https".equalsIgnoreCase(parsed.getScheme()))
            {
                if (parsed.getHost() == null || parsed.getUserInfo() != null)
                {
                    throw new NDefValueException("Expected a web URI with a host and without user information");
                }
            }

            uri = parsed.toASCIIString();
        }
        catch (URISyntaxException exception)
        {
            throw new NDefValueException("Invalid URI", exception);
        }
    }
}
