package com.rvi.presentation;

import com.rvi.application.NDefEncoder;
import com.rvi.application.ReaderListener;
import com.rvi.application.ReaderMonitor;
import com.rvi.application.ReaderStatus;
import com.rvi.domain.NDefMessage;
import org.jline.keymap.KeyMap;
import org.jline.keymap.BindingReader;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.Display;
import org.jline.utils.InfoCmp.Capability;

import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.TerminalFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ConsoleApplication
{
    private final List<ReaderMonitor> monitors = new ArrayList<>();
    private final ConcurrentLinkedQueue<Runnable> events = new ConcurrentLinkedQueue<>();
    private final MainWindow window = new MainWindow();
    private List<CardTerminal> terminals = List.of();
    private ReaderListener listener;
    private boolean closing;
    private boolean finished;
    private boolean pasting;
    private long generation;
    private int totalSuccessfulCards;
    private int previousSuccessfulCards;

    public void run() throws Exception
    {
        try (Terminal terminal = TerminalBuilder.builder().name("SocialCard")
                .system(true).provider("jni").dumb(false).build())
        {
            final Attributes original = terminal.enterRawMode();
            final Terminal.SignalHandler previous = terminal.handle(Terminal.Signal.INT,
                    signal -> events.add(this::exit));
            try
            {
                terminal.puts(Capability.enter_ca_mode);
                terminal.puts(Capability.cursor_invisible);
                terminal.writer().print("\033[?2004h");
                terminal.flush();
                final Display display = new Display(terminal, true);
                final BindingReader reader = new BindingReader(terminal.reader());
                final KeyMap<String> keys = bindings();
                refresh();
                while (!finished)
                {
                    Runnable event;
                    while ((event = events.poll()) != null) { event.run(); }
                    if (finished) { break; }
                    final int width = Math.max(1, terminal.getWidth());
                    final int height = Math.max(1, terminal.getHeight());
                    display.resize(height, width);
                    display.update(window.render(width, height, System.nanoTime() / 150_000_000L), 0);
                    if (reader.peekCharacter(80) == -1) { exit(); continue; }
                    if (reader.peekCharacter(1) < 0) { continue; }
                    final String key = reader.readBinding(keys);
                    if (key != null) { handle(key, reader.getLastBinding()); }
                }
            }
            finally
            {
                if (listener != null) { listener.deactivate(); }
                monitors.forEach(ReaderMonitor::close);
                try
                {
                    for (final ReaderMonitor monitor : monitors) { monitor.awaitStopped(); }
                }
                finally
                {
                    terminal.writer().print("\033[?2004l");
                    terminal.puts(Capability.cursor_normal);
                    terminal.puts(Capability.exit_ca_mode);
                    terminal.setAttributes(original);
                    terminal.handle(Terminal.Signal.INT, previous);
                    terminal.flush();
                }
            }
        }
    }

    private static KeyMap<String> bindings()
    {
        final KeyMap<String> keys = new KeyMap<>();
        keys.setUnicode("TEXT");
        keys.setNomatch("TEXT");
        keys.setAmbiguousTimeout(100);
        keys.bind("TAB", "\t", "\033[Z");
        keys.bind("ENTER", "\r", "\n");
        keys.bind("EXIT", "\033", "\003");
        keys.bind("BACKSPACE", "\177", "\b");
        keys.bind("CLEAR", "\025");
        keys.bind("LEFT", "\033[D", "\033OD");
        keys.bind("RIGHT", "\033[C", "\033OC");
        keys.bind("PREVIOUS", "\033[A", "\033OA");
        keys.bind("NEXT", "\033[B", "\033OB");
        keys.bind("PASTE_START", "\033[200~");
        keys.bind("PASTE_END", "\033[201~");
        return keys;
    }

    private void handle(final String key, final String text)
    {
        if (key.equals("PASTE_START")) { pasting = true; return; }
        if (key.equals("PASTE_END")) { pasting = false; return; }
        if (closing) { return; }
        if (pasting)
        {
            if (key.equals("TEXT")) { window.insert(text); }
            return;
        }
        switch (key)
        {
            case "EXIT" -> exit();
            case "TAB" -> window.nextFocus();
            case "BACKSPACE" -> window.backspace();
            case "CLEAR" -> window.clearUrl();
            case "LEFT", "PREVIOUS" -> window.move(-1);
            case "RIGHT", "NEXT" -> window.move(1);
            case "ENTER" ->
            {
                if (window.isActive()) { stop(); }
                else if (window.focus() == 0) { window.nextFocus(); }
                else { activate(); }
            }
            case "TEXT" ->
            {
                if (window.focus() != 1 && text.equalsIgnoreCase("r") && !window.isActive()) { refresh(); }
                else { window.insert(text); }
            }
            default -> { }
        }
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
        if (closing || window.isActive() || window.selectedReader() < 0)
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
            listener = new ReaderListener(update -> events.add(() ->
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
            finished = true;
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
                finished = true;
            }
        }
    }
}
