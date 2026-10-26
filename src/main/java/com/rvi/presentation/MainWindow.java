package com.rvi.presentation;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.rvi.application.ReaderStatus;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.List;

public final class MainWindow extends BasicWindow
{
    private final ComboBox<String> readers = new ComboBox<String>().setReadOnly(true);
    private final TextBox url = new TextBox(new TerminalSize(56, 1));
    private final Label status = new Label("Bereit. Schreibauftrag aktivieren.")
            .setLabelWidth(64).setPreferredSize(new TerminalSize(64, 3));
    private final Label count = new Label("0");
    private final TextBox events = new TextBox(new TerminalSize(64, 6), TextBox.Style.MULTI_LINE)
            .setReadOnly(true);
    private final ArrayDeque<String> history = new ArrayDeque<>();
    private final Button activate;
    private final Button stop;
    private final Button refresh;
    private final Button exit;
    private final Runnable onExit;

    public MainWindow(final Runnable onActivate, final Runnable onStop,
                      final Runnable onRefresh, final Runnable onExit)
    {
        super("SocialCard - NFC Link Manager");
        this.onExit = onExit;
        setHints(List.of(Hint.CENTERED));
        setCloseWindowWithEscape(false);

        final Panel content = new Panel(new LinearLayout(Direction.VERTICAL).setSpacing(0));
        content.addComponent(new Label("SOCIALCARD").setForegroundColor(TextColor.ANSI.CYAN));
        content.addComponent(new Label("HTTPS-Links auf NFC-Karten schreiben"));

        final Panel form = new Panel(new GridLayout(2));
        form.addComponent(new Label("Reader"));
        readers.setPreferredSize(new TerminalSize(56, 1));
        form.addComponent(readers);
        form.addComponent(new Label("URL"));
        form.addComponent(url);
        form.addComponent(new Label("Modus"));
        form.addComponent(new Label("Serienmodus - ein Link für mehrere Karten"));
        content.addComponent(form.withBorder(Borders.singleLine("Schreibauftrag")));

        final Panel actions = new Panel(new LinearLayout(Direction.HORIZONTAL).setSpacing(1));
        activate = new Button("Schreiben aktivieren", onActivate);
        stop = new Button("Stoppen", onStop);
        refresh = new Button("Reader aktualisieren", onRefresh);
        stop.setEnabled(false);
        actions.addComponent(activate);
        actions.addComponent(stop);
        actions.addComponent(refresh);
        content.addComponent(actions);

        final Panel state = new Panel(new LinearLayout(Direction.VERTICAL));
        state.addComponent(status);
        final Panel counter = new Panel(new LinearLayout(Direction.HORIZONTAL));
        counter.addComponent(new Label("Erfolgreich beschrieben:"));
        counter.addComponent(count);
        state.addComponent(counter);
        content.addComponent(state.withBorder(Borders.singleLine("Status")));
        content.addComponent(events.withBorder(Borders.singleLine("Letzte Ereignisse")));

        exit = new Button("Beenden", onExit);
        content.addComponent(exit);
        content.addComponent(new Label("Tab: Feld wechseln   Enter: Auswählen   Esc: Beenden"));
        setComponent(content);
    }

    public void setReaders(final List<String> names)
    {
        final String previous = readers.getSelectedItem();
        readers.clearItems();
        names.forEach(readers::addItem);
        if (previous != null && names.contains(previous))
        {
            readers.setSelectedItem(previous);
        }
        activate.setEnabled(!names.isEmpty());
    }

    public int selectedReader()
    {
        return readers.getSelectedIndex();
    }

    public String url()
    {
        return url.getText();
    }

    public void setActive(final boolean active)
    {
        readers.setEnabled(!active);
        url.setEnabled(!active);
        activate.setEnabled(!active && readers.getItemCount() > 0);
        refresh.setEnabled(!active);
        stop.setEnabled(active);
    }

    public void setClosing()
    {
        setActive(true);
        stop.setEnabled(false);
        exit.setEnabled(false);
        showMessage("Beende nach dem laufenden Schreibvorgang ...", false);
    }

    public void showStatus(final ReaderStatus update)
    {
        count.setText(Integer.toString(update.successfulCards()));
        status.setForegroundColor(switch (update.state())
        {
            case ERROR -> TextColor.ANSI.RED;
            case SUCCESS -> TextColor.ANSI.GREEN;
            default -> TextColor.ANSI.CYAN;
        });
        status.setText(update.message());
        addEvent(update.message());
    }

    public void showMessage(final String text, final boolean error)
    {
        status.setForegroundColor(error ? TextColor.ANSI.RED : TextColor.ANSI.CYAN);
        status.setText(text);
        addEvent(text);
    }

    private void addEvent(final String text)
    {
        history.addLast(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                + "  " + text.replaceAll("[\\r\\n\\p{Cntrl}]", " "));
        while (history.size() > 100)
        {
            history.removeFirst();
        }
        events.setText(String.join("\n", history));
        events.setCaretPosition(0, events.getLineCount() - 1);
    }

    @Override
    public boolean handleInput(final KeyStroke key)
    {
        if (key.getKeyType() == KeyType.Escape)
        {
            onExit.run();
            return true;
        }
        return super.handleInput(key);
    }
}
