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
        final String url = service.createUrl("https://example.com/", "Rithush & Co", "Hi + 👋", links);
        assertTrue(url.contains("#v=1&n=Rithush%20%26%20Co&b=Hi%20%2B%20%F0%9F%91%8B"));
        assertNull(URI.create(url).getQuery());
        assertTrue(service.encodedBytes(url) <= 257);
    }

    @Test
    void rejectsInvalidViewerAndUnsafeLinks()
    {
        for (final String viewer : List.of("http://example.com", "https://u:p@example.com",
                "https://example.com/#old", "https://example.com/?old=1"))
            assertThrows(IllegalArgumentException.class,
                    () -> service.createUrl(viewer, "Rithush", "", links));
        assertThrows(IllegalArgumentException.class, () -> service.createUrl(
                "https://example.com", "Rithush", "",
                List.of(new ProfileService.Link("Click", "javascript:alert(1)"))));
        assertThrows(IllegalArgumentException.class,
                () -> service.createUrl("https://example.com", "", "", links));
    }

    @Test
    void refusesOversizedProfileBeforeWriting()
    {
        final String url = service.createUrl("https://example.com/", "Rithush",
                "x".repeat(200), links);
        assertThrows(IllegalArgumentException.class, () -> service.encodedBytes(url));
    }
}
