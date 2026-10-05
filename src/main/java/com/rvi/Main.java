package com.rvi;

import com.rvi.application.IReaderRepository;
import com.rvi.application.ReaderRepository;
import com.rvi.domain.NDefMessage;
import com.rvi.service.IReaderService;
import com.rvi.service.ReaderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.*;
import java.util.HexFormat;
import java.util.List;

public class Main
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    public static void main(String[] args) throws CardException
    {

        final List<CardTerminal> terminals = TerminalFactory.getDefault().terminals().list();

        if (terminals.isEmpty())
        {
            LOGGER.info("No terminal found");
            return;
        }

        final CardTerminal terminal = terminals.getFirst();

        if (!terminal.isCardPresent())
        {
            LOGGER.info("Card not found");
            return;
        }

        final Card card = terminal.connect("*");

        try
        {
            final IReaderRepository repository = new ReaderRepository(card.getBasicChannel());

            final IReaderService service = new ReaderService(repository);

            final NDefMessage message = service.encodeURIs(List.of("https://github.com/Vijiyarathan-Rithush/socialcard", "https://github.com/Vijiyarathan-Rithush/socialcard/tree/main/src/main/java/com/rvi", "https://www.youtube.com/"));

            service.write(message);

            service.write(message);
            LOGGER.info("Link saved successfully");
        }
        finally
        {
            card.disconnect(false);
        }
    }
}