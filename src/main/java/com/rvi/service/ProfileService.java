package com.rvi.service;

import com.rvi.application.NDefEncoder;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Version 1 profile fragments shared with the static viewer in docs/. */
public final class ProfileService
{
    public record Link(String label, String url) {}

    public String createUrl(final String viewer, final String name, final String bio,
                            final List<Link> links)
    {
        final URI base = https(viewer);
        if (base.getRawFragment() != null || base.getRawQuery() != null)
            throw new IllegalArgumentException("Viewer URL must not contain a query or fragment");
        final String title = required(name, "Name", 100);
        final String description = bio == null ? "" : bio.strip();
        if (description.length() > 200)
            throw new IllegalArgumentException("Description must be at most 200 characters");
        if (links == null || links.isEmpty() || links.size() > 10)
            throw new IllegalArgumentException("Add between 1 and 10 links");

        final var fragment = new StringBuilder("v=1&n=").append(encode(title));
        if (!description.isEmpty()) fragment.append("&b=").append(encode(description));
        for (final Link link : links)
        {
            if (link == null) throw new IllegalArgumentException("Link is missing");
            final String label = required(link.label(), "Link label", 60);
            final String url = https(link.url()).toASCIIString();
            fragment.append("&l=").append(encode(label)).append("&u=").append(encode(url));
        }
        return base.toASCIIString() + "#" + fragment;
    }

    /** Includes the TLV and terminator, matching the bytes written to the card. */
    public int encodedBytes(final String url)
    {
        try { return new NDefEncoder().encodeURI(url).data().length; }
        catch (com.rvi.application.exception.NDefEncodingException exception)
        {
            throw new IllegalArgumentException("Profile exceeds supported card size; shorten the viewer URL, description or links");
        }
    }

    private static String required(final String value, final String field, final int limit)
    {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " is required");
        final String stripped = value.strip();
        if (stripped.length() > limit)
            throw new IllegalArgumentException(field + " must be at most " + limit + " characters");
        return stripped;
    }

    private static URI https(final String value)
    {
        final URI uri;
        try { uri = URI.create(required(value, "URL", 2048)); }
        catch (IllegalArgumentException exception)
        {
            throw new IllegalArgumentException("Use a valid HTTPS URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null)
            throw new IllegalArgumentException("Use a valid HTTPS URL without credentials");
        return uri;
    }

    private static String encode(final String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
