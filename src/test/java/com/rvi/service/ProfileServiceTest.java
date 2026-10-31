package com.rvi.service;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProfileServiceTest
{
    private final ProfileService service = new ProfileService();
    private final List<ProfileService.Link> links =
            List.of(new ProfileService.Link("GitHub", "https://github.com/rvi"));

    @Test
    void createsVersionedFragmentAndChecksActualNdefBytes()
    {
        final String url = service.createUrl("Rithush & Co", "Hi + 👋", links);
        assertTrue(url.contains("#v=1&n=Rithush%20%26%20Co&b=Hi%20%2B%20%F0%9F%91%8B"));
        assertNull(URI.create(url).getQuery());
        assertEquals("rithush.dev", URI.create(url).getHost());
        assertTrue(service.encodedBytes(url) <= 496);
    }

    @Test
    void rejectsMissingNameAndUnsafeLinks()
    {
        assertThrows(IllegalArgumentException.class, () -> service.createUrl(
                "Rithush", "",
                List.of(new ProfileService.Link("Click", "javascript:alert(1)"))));
        assertThrows(IllegalArgumentException.class,
                () -> service.createUrl("", "", links));
    }

    @Test
    void storesProfilesBeyondTheOldShortTlvLimit()
    {
        final String url = service.createUrl("Rithush", "x".repeat(200), links);
        assertTrue(service.encodedBytes(url) > 257);
        assertTrue(service.encodedBytes(url) <= 496);
    }

    @Test
    void refusesOversizedProfileBeforeWriting()
    {
        final String url = service.createUrl("Rithush",
                "x".repeat(200), List.of(new ProfileService.Link("Long", "https://a.ch/" + "x".repeat(400))));
        assertThrows(IllegalArgumentException.class, () -> service.encodedBytes(url));
    }
}
