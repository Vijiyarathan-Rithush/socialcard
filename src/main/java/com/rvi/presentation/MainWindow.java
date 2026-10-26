package com.rvi.presentation;

import com.rvi.application.ReaderStatus;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Borderless terminal view. All mutations happen on the application thread. */
public final class MainWindow
{
    private static final AttributedStyle TEXT = AttributedStyle.DEFAULT;
    private static final AttributedStyle MUTED = TEXT.foreground(245);
    private static final AttributedStyle ACCENT = TEXT.foreground(141);
    private static final AttributedStyle SUCCESS = TEXT.foreground(114);
    private static final AttributedStyle ERROR = TEXT.foreground(210);
    private final StringBuilder url = new StringBuilder();
    private final ArrayDeque<String> history = new ArrayDeque<>();
    private List<String> readers = List.of();
    private int readerIndex;
    private int cursor;
    private int focus = 1;
    private boolean active;
    private boolean closing;
    private ReaderStatus.State state = ReaderStatus.State.READY;
    private String message = "Link eingeben und eine neue Serie starten.";
    private int successfulCards;

    public void setReaders(final List<String> names)
    {
        final String previous = readers.isEmpty() ? null : readers.get(readerIndex);
        readers = List.copyOf(names);
        readerIndex = previous == null ? 0 : Math.max(0, readers.indexOf(previous));
    }

    public int selectedReader() { return readers.isEmpty() ? -1 : readerIndex; }
    public String url() { return url.toString(); }
    public boolean isActive() { return active; }
    public int focus() { return focus; }
    public void nextFocus() { if (!active) { focus = (focus + 1) % 3; } }
    public void setActive(final boolean value) { active = value; focus = value ? 2 : 1; }
    public void setClosing() { closing = true; showMessage("Laufenden Schreibvorgang abschliessen ...", false); }

    public void move(final int direction)
    {
        if (active) { return; }
        if (focus == 0 && !readers.isEmpty())
        {
            readerIndex = Math.floorMod(readerIndex + direction, readers.size());
        }
        else if (focus == 1)
        {
            if (direction < 0 && cursor > 0) { cursor = url.offsetByCodePoints(cursor, -1); }
            if (direction > 0 && cursor < url.length()) { cursor = url.offsetByCodePoints(cursor, 1); }
        }
    }

    public void insert(final String text)
    {
        if (!active && focus == 1)
        {
            final String cleaned = clean(text);
            if (url.length() + cleaned.length() <= 4096)
            {
                url.insert(cursor, cleaned);
                cursor += cleaned.length();
            }
        }
    }

    public void backspace()
    {
        if (!active && focus == 1 && cursor > 0)
        {
            final int previous = url.offsetByCodePoints(cursor, -1);
            url.delete(previous, cursor);
            cursor = previous;
        }
    }

    public void clearUrl()
    {
        if (!active && focus == 1) { url.setLength(0); cursor = 0; }
    }

    public void showStatus(final ReaderStatus update)
    {
        state = update.state();
        successfulCards = update.successfulCards();
        message = clean(update.message());
        addEvent(message);
    }

    public void showMessage(final String text, final boolean error)
    {
        state = error ? ReaderStatus.State.ERROR : ReaderStatus.State.READY;
        message = clean(text);
        addEvent(message);
    }

    private void addEvent(final String text)
    {
        history.addFirst(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + "  " + text);
        while (history.size() > 100) { history.removeLast(); }
    }

    public List<AttributedString> render(final int width, final int height, final long tick)
    {
        final List<AttributedString> lines = new ArrayList<>();
        if (width < 60 || height < 22)
        {
            lines.add(line(ACCENT.bold(), "  socialcard"));
            lines.add(line(TEXT, "  Terminal vergrössern"));
            lines.add(line(MUTED, "  Mindestens 60 Spalten × 22 Zeilen"));
            lines.add(line(MUTED, "  Esc  Beenden"));
            return fit(lines, width, height);
        }
        final int contentWidth = Math.min(76, width - 8);
        final String margin = " ".repeat(Math.max(3, (width - contentWidth) / 2));
        lines.add(AttributedString.EMPTY);
        lines.add(new AttributedStringBuilder().append(margin).style(TEXT.bold()).append("socialcard")
                .style(MUTED).append("  /  nfc studio").toAttributedString());
        lines.add(line(MUTED, margin + "Ein Link. Eine Berührung. Bereit zum Teilen."));
        lines.add(AttributedString.EMPTY);
        lines.add(line(focus == 0 && !active ? ACCENT : MUTED, margin + "01  READER"));
        final String reader = readers.isEmpty() ? "Kein Reader verbunden" : clean(readers.get(readerIndex));
        lines.add(new AttributedStringBuilder().append(margin).style(readers.isEmpty() ? ERROR : SUCCESS)
                .append(readers.isEmpty() ? "○  " : "●  ").style(TEXT).append(reader)
                .style(MUTED).append(readers.size() > 1 ? "   ‹ / ›" : "").toAttributedString());
        lines.add(AttributedString.EMPTY);
        lines.add(line(focus == 1 && !active ? ACCENT : MUTED, margin + "02  DESTINATION"));
        final int visible = contentWidth - 4;
        final int start = Math.max(0, cursor - visible + 1);
        final String before = url.substring(start, cursor);
        final String after = url.substring(cursor);
        final AttributedStringBuilder input = new AttributedStringBuilder().append(margin).style(ACCENT).append("› ");
        if (url.isEmpty() && (focus != 1 || active))
        {
            input.style(MUTED).append("https://dein-link.ch");
        }
        else
        {
            input.style(TEXT).append(before);
            if (focus == 1 && !active && !closing)
            {
                input.style(ACCENT.inverse()).append(after.isEmpty() ? " " : after.substring(0, 1));
                input.style(TEXT).append(after.isEmpty() ? "" : after.substring(1));
            }
            else { input.style(TEXT).append(after); }
        }
        lines.add(input.toAttributedString());
        lines.add(line(MUTED, margin + (active ? "Link für die laufende Serie gesperrt" : "HTTPS-Link eingeben oder einfügen")));
        lines.add(AttributedString.EMPTY);
        final AttributedStyle stateColor = state == ReaderStatus.State.ERROR ? ERROR
                : state == ReaderStatus.State.SUCCESS ? SUCCESS : ACCENT;
        final String label = closing ? "BEENDEN" : switch (state)
        {
            case WRITING -> "SCHREIBT " + "◐◓◑◒".charAt((int) (tick % 4));
            case SUCCESS -> "VERIFIZIERT";
            case ERROR -> "FEHLER";
            case ACTIVE -> "WARTE AUF KARTE";
            case STOPPING -> "STOPPT";
            default -> "BEREIT";
        };
        lines.add(new AttributedStringBuilder().append(margin).style(stateColor.bold()).append(label)
                .style(MUTED).append("    ·    ").style(TEXT.bold()).append(Integer.toString(successfulCards))
                .style(MUTED).append(" Karten verifiziert").toAttributedString());
        final AttributedString status = line(TEXT, message);
        final List<AttributedString> wrapped = status.columnSplitLength(contentWidth);
        lines.add(line(TEXT, margin + (wrapped.isEmpty() ? "" : wrapped.getFirst().toString())));
        lines.add(line(TEXT, margin + (wrapped.size() > 1 ? wrapped.get(1).toString() : "")));
        lines.add(AttributedString.EMPTY);
        lines.add(new AttributedStringBuilder().append(margin).style(focus == 2 ? ACCENT.inverse() : MUTED.inverse())
                .append(" enter ").style(TEXT).append(active ? " Serie stoppen" : " Serie starten")
                .style(MUTED).append("     tab  Wechseln     esc  Beenden").toAttributedString());
        lines.add(AttributedString.EMPTY);
        lines.add(line(MUTED, margin + "AKTIVITÄT"));
        int shown = 0;
        for (final String event : history)
        {
            if (shown++ == 3) { break; }
            lines.add(line(MUTED, margin + event));
        }
        while (lines.size() < height - 2) { lines.add(AttributedString.EMPTY); }
        lines.add(line(MUTED, margin + (active ? "Serienmodus aktiv" : "r  Reader aktualisieren (ausserhalb des URL-Felds)")));
        return fit(lines, width, height);
    }

    private static AttributedString line(final AttributedStyle style, final String text)
    {
        return new AttributedString(text, style);
    }

    private static List<AttributedString> fit(final List<AttributedString> lines, final int width, final int height)
    {
        // JLine retains and mutates frames during scrolling and full redraws.
        return lines.stream().limit(Math.max(1, height)).map(value ->
                value.columnSubSequence(0, Math.max(1, width - 1)))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private static String clean(final String text)
    {
        return text.replaceAll("[\\p{Cntrl}\\p{Cf}]", "");
    }
}
