package com.rvi.presentation;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.PropertyTheme;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import com.googlecode.lanterna.gui2.DefaultWindowManager;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.rvi.application.NDefEncoder;
import com.rvi.application.ReaderListener;
import com.rvi.application.ReaderMonitor;
import com.rvi.application.ReaderStatus;
import com.rvi.domain.NDefMessage;

import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.TerminalFactory;
import java.util.ArrayList;
import java.util.List;

public final class ConsoleApplication
{
    private final List<ReaderMonitor> monitors = new ArrayList<>();
    private List<CardTerminal> terminals = List.of();
    private MultiWindowTextGUI gui;
    private MainWindow window;
    private ReaderListener listener;
    private boolean closing;
    private long generation;
    private int totalSuccessfulCards;
    private int previousSuccessfulCards;

    public void run() throws Exception
    {
        final DefaultTerminalFactory factory = new DefaultTerminalFactory()
                .setInitialTerminalSize(new TerminalSize(90, 34))
                .setTerminalEmulatorTitle("SocialCard");
        try (TerminalScreen screen = factory.createScreen())
        {
            screen.startScreen();
            gui = new MultiWindowTextGUI(screen, new DefaultWindowManager(),
                    new EmptySpace(TextColor.ANSI.BLACK));
            gui.setTheme(createTheme());
            window = new MainWindow(this::activate, this::stop, this::refresh, this::exit);
            refresh();
            try
            {
                gui.addWindowAndWait(window);
            }
            finally
            {
                if (listener != null)
                {
                    listener.deactivate();
                }
                monitors.forEach(ReaderMonitor::close);
                for (final ReaderMonitor monitor : monitors)
                {
                    monitor.awaitStopped();
                }
            }
        }
    }

    static PropertyTheme createTheme() throws IOException
    {
        final Properties properties = new Properties();
        try (InputStream input = ConsoleApplication.class.getResourceAsStream("/businessmachine-theme.properties"))
        {
            if (input == null)
            {
                throw new IOException("Terminal theme is missing");
            }
            properties.load(input);
        }
        properties.replaceAll((key, value) -> "green".equals(value) ? "white" : value);
        properties.setProperty("foreground[ACTIVE]", "black");
        properties.setProperty("background[ACTIVE]", "cyan");
        properties.setProperty("foreground[SELECTED]", "black");
        properties.setProperty("background[SELECTED]", "cyan");
        properties.setProperty("foreground[INSENSITIVE]", "#777777");
        return new PropertyTheme(properties);
    }

    private void refresh()
    {
        try
        {
            terminals = TerminalFactory.getDefault().terminals().list();
            window.setReaders(terminals.stream().map(CardTerminal::getName).toList());
            window.showMessage(terminals.isEmpty()
                    ? "Kein Reader gefunden. Anschliessen und aktualisieren."
                    : "Reader bereit. HTTPS-Link eingeben und Schreiben aktivieren.", terminals.isEmpty());
        }
        catch (CardException | RuntimeException exception)
        {
            terminals = List.of();
            window.setReaders(List.of());
            window.showMessage("Reader konnten nicht geladen werden: " + exception.getMessage(), true);
        }
    }

    private void activate()
    {
        if (closing || window.selectedReader() < 0)
        {
            return;
        }
        try
        {
            final NDefMessage message = new NDefEncoder().encodeURI(window.url());
            if (listener != null)
            {
                listener.deactivate();
            }
            monitors.forEach(ReaderMonitor::close);
            final long currentGeneration = ++generation;
            previousSuccessfulCards = 0;
            listener = new ReaderListener(update -> gui.getGUIThread().invokeLater(() ->
            {
                if (currentGeneration == generation)
                {
                    onStatus(update);
                }
            }));
            final ReaderMonitor monitor = new ReaderMonitor(
                    terminals.get(window.selectedReader()), listener);
            monitors.add(monitor);
            window.setActive(true);
            listener.activate(message);
            monitor.start();
        }
        catch (RuntimeException exception)
        {
            window.showMessage("Auftrag konnte nicht aktiviert werden: " + exception.getMessage(), true);
        }
    }

    private void stop()
    {
        if (listener != null)
        {
            listener.deactivate();
        }
        monitors.forEach(ReaderMonitor::close);
    }

    private void exit()
    {
        if (closing)
        {
            return;
        }
        closing = true;
        stop();
        if (listener != null && listener.isWriting())
        {
            window.setClosing();
        }
        else
        {
            window.close();
        }
    }

    private void onStatus(final ReaderStatus update)
    {
        totalSuccessfulCards += update.successfulCards() - previousSuccessfulCards;
        previousSuccessfulCards = update.successfulCards();
        window.showStatus(new ReaderStatus(update.state(), update.message(), totalSuccessfulCards));
        if (update.state() == ReaderStatus.State.STOPPED)
        {
            window.setActive(false);
            if (closing)
            {
                window.close();
            }
        }
    }
}
