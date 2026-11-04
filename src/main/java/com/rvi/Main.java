package com.rvi;

import com.rvi.application.creator.NDefMessageCreator;
import com.rvi.application.creator.NDefTextCreator;
import com.rvi.application.creator.NDefUriCreator;
import com.rvi.domain.NDefMessage;
import com.rvi.domain.NDefText;
import com.rvi.domain.NDefUri;
import com.rvi.service.IReaderService;
import com.rvi.service.ReaderService;

public final class Main
{
    public static void main(final String[] args)
    {
        final NDefMessage message = createMessage();

        try (final IReaderService service = ReaderService.create())
        {
            service.connect();
            service.write(message);

            final NDefMessage stored = service.read();
            System.out.println("Card written and verified: %d NDEF bytes".formatted(stored.data().length));
        }
    }

    private static NDefMessage createMessage()
    {
        final NDefUri website = new NDefUriCreator().uri("https://rithush.dev").build();
        final NDefText text = new NDefTextCreator().text("Welcome to my SocialCard").language("en").build();

        return new NDefMessageCreator().add(website).add(text).build();
    }
}
