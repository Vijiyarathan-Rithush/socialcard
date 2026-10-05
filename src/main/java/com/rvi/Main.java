package com.rvi;

import com.rvi.application.IReaderRepository;
import com.rvi.application.ReaderRepository;
import com.rvi.domain.NDefMessage;
import com.rvi.service.IReaderService;
import com.rvi.service.ReaderService;

import javax.smartcardio.*;
import java.util.HexFormat;
import java.util.List;

public class Main
{

    public static void main(String[] args) throws CardException
    {
        final List<CardTerminal> terminals = TerminalFactory.getDefault().terminals().list();

        if (terminals.isEmpty())
        {
            System.out.println("No Card terminal found.");
            return;
        }

        final CardTerminal terminal = terminals.getFirst();

        if (!terminal.isCardPresent())
        {
            System.out.println("Please present the card");
            return;
        }

        final Card card = terminal.connect("*");

        try
        {
            final IReaderRepository repository = new ReaderRepository(card.getBasicChannel());

            final IReaderService service = new ReaderService(repository);

            final NDefMessage message = service.encodeURI("https://github.com/Vijiyarathan-Rithush");

            service.write(message);

            final NDefMessage stored = service.read();

            service.write(message);
            System.out.println("Link Saved");

            System.out.println(HexFormat.ofDelimiter(" ").withUpperCase().formatHex(stored.data()));
        }
        finally
        {
            card.disconnect(false);
        }
    }
}