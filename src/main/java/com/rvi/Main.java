package com.rvi;

import com.rvi.application.*;
import com.rvi.domain.NDefMessage;
import com.rvi.service.IReaderService;
import com.rvi.service.ReaderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.*;
import java.util.List;

public class Main
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    public static void main(String[] args) throws Exception
    {
        final List<CardTerminal> terminals =
                TerminalFactory.getDefault().terminals().list();

        if (terminals.isEmpty())
        {
            LOGGER.info("No terminal found");
            return;
        }

        final NDefMessage message = new NDefEncoder().encodeURI(
                "https://youtube.com");

        final IReaderListener listener = new ReaderListener(message);

        try (ReaderMonitor monitor = new ReaderMonitor(terminals.getFirst(), listener))
        {
            monitor.start();

            System.out.println("Place a card on the reader.");
            System.out.println("Press Enter to exit.");

            new java.util.Scanner(System.in).nextLine();
        }
    }
}