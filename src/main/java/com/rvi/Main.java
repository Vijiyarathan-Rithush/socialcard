package com.rvi;

import com.rvi.application.IReaderListener;
import com.rvi.application.ReaderListener;
import com.rvi.application.ReaderMonitor;
import com.rvi.application.creator.NDefMessageCreator;
import com.rvi.application.creator.NDefTextCreator;
import com.rvi.application.creator.NDefUriCreator;
import com.rvi.domain.NDefMessage;
import com.rvi.domain.NDefText;
import com.rvi.domain.NDefUri;

import javax.smartcardio.CardTerminal;
import javax.smartcardio.TerminalFactory;
import java.util.List;
import java.util.Scanner;

public final class Main
{
    public static void main(final String[] args) throws Exception
    {
        final NDefMessage message = createMessage();

        runReader(message);
    }

    private static NDefMessage createMessage()
    {
        final NDefUri website = new NDefUriCreator().uri("https://rithush.dev").build();
        final NDefText text = new NDefTextCreator().text("Willkommen auf meiner SocialCard").language("de").build();

        return new NDefMessageCreator().add(website).add(text).build();
    }

    private static void runReader(final NDefMessage message) throws Exception
    {
        final List<CardTerminal> terminals = TerminalFactory.getDefault().terminals().list();

        if (terminals.isEmpty())
        {
            System.out.println("Kein NFC-Reader gefunden.");
            return;
        }

        final IReaderListener listener = new ReaderListener(message);

        try (ReaderMonitor monitor = new ReaderMonitor(terminals.getFirst(), listener))
        {
            monitor.start();

            System.out.println("Karte auflegen. Enter zum Beenden.");

            final Scanner scanner = new Scanner(System.in);
            scanner.nextLine();
        }
    }
}
