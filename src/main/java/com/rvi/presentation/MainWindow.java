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
import java.util.stream.Collectors;

/** Borderless terminal view. All mutations happen on the application thread. */
public final class MainWindow
{
    private static final int MINIMUM_WIDTH = 60;
    private static final int MINIMUM_HEIGHT = 22;
    private static final int MAXIMUM_CONTENT_WIDTH = 76;
    private static final int MAXIMUM_URL_LENGTH = 4096;
    private static final int HISTORY_LIMIT = 100;
    private static final int VISIBLE_EVENTS = 3;
    private static final String SPINNER_FRAMES = "◐◓◑◒";
    private static final DateTimeFormatter EVENT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public enum Focus
    {
        READER,
        DESTINATION,
        ACTION
    }

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
    private Focus focus = Focus.DESTINATION;
    private boolean active;
    private boolean closing;
    private ReaderStatus.State state = ReaderStatus.State.READY;
    private String message = "Enter a link to start a new batch.";
    private int successfulCards;

    public void setReaders(final List<String> names)
    {
        final String previous = readers.isEmpty() ? null : readers.get(readerIndex);
        readers = List.copyOf(names);
        readerIndex = previous == null ? 0 : Math.max(0, readers.indexOf(previous));
    }

    public int selectedReader()
    {
        return readers.isEmpty() ? -1 : readerIndex;
    }

    public String url()
    {
        return url.toString();
    }

    public boolean isActive()
    {
        return active;
    }

    public Focus focus()
    {
        return focus;
    }

    public void nextFocus()
    {
        if (!active)
        {
            focus = Focus.values()[(focus.ordinal() + 1) % Focus.values().length];
        }
    }

    public void setActive(final boolean value)
    {
        active = value;
        focus = value ? Focus.ACTION : Focus.DESTINATION;
    }

    public void setClosing()
    {
        closing = true;
        showMessage("Finishing the current write ...", false);
    }

    public void move(final int direction)
    {
        if (active)
        {
            return;
        }
        if (focus == Focus.READER && !readers.isEmpty())
        {
            readerIndex = Math.floorMod(readerIndex + direction, readers.size());
        }
        else if (focus == Focus.DESTINATION)
        {
            if (direction < 0 && cursor > 0)
            {
                cursor = url.offsetByCodePoints(cursor, -1);
            }
            if (direction > 0 && cursor < url.length())
            {
                cursor = url.offsetByCodePoints(cursor, 1);
            }
        }
    }

    public void insert(final String text)
    {
        if (!active && focus == Focus.DESTINATION)
        {
            final String cleaned = clean(text);
            if (url.length() + cleaned.length() <= MAXIMUM_URL_LENGTH)
            {
                url.insert(cursor, cleaned);
                cursor += cleaned.length();
            }
        }
    }

    public void backspace()
    {
        if (!active && focus == Focus.DESTINATION && cursor > 0)
        {
            final int previous = url.offsetByCodePoints(cursor, -1);
            url.delete(previous, cursor);
            cursor = previous;
        }
    }

    public void clearUrl()
    {
        if (!active && focus == Focus.DESTINATION)
        {
            url.setLength(0);
            cursor = 0;
        }
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
        history.addFirst(LocalTime.now().format(EVENT_TIME_FORMAT) + "  " + text);
        while (history.size() > HISTORY_LIMIT)
        {
            history.removeLast();
        }
    }

    public List<AttributedString> render(final int width, final int height, final long tick)
    {
        final List<AttributedString> lines = new ArrayList<>();
        if (width < MINIMUM_WIDTH || height < MINIMUM_HEIGHT)
        {
            lines.add(line(ACCENT.bold(), "  socialcard"));
            lines.add(line(TEXT, "  Resize your terminal"));
            lines.add(line(MUTED, "  At least 60 columns × 22 rows"));
            lines.add(line(MUTED, "  Esc  Exit"));
            return fit(lines, width, height);
        }
        final int contentWidth = Math.min(MAXIMUM_CONTENT_WIDTH, width - 8);
        final String margin = " ".repeat(Math.max(3, (width - contentWidth) / 2));
        appendHeader(lines, margin);
        appendReader(lines, margin);
        appendDestination(lines, margin, contentWidth);
        appendStatus(lines, margin, contentWidth, tick);
        appendActions(lines, margin);
        appendActivity(lines, margin);
        while (lines.size() < height - 2)
        {
            lines.add(AttributedString.EMPTY);
        }
        lines.add(
                line(
                        MUTED,
                        margin
                                + (active
                                        ? "Batch mode active"
                                        : "r  Refresh readers (outside the URL field)")));
        return fit(lines, width, height);
    }

    private void appendHeader(final List<AttributedString> lines, final String margin)
    {
        lines.add(AttributedString.EMPTY);
        lines.add(
                new AttributedStringBuilder()
                        .append(margin)
                        .style(TEXT.bold())
                        .append("socialcard")
                        .style(MUTED)
                        .append("  /  nfc studio")
                        .toAttributedString());
        lines.add(line(MUTED, margin + "One link. One tap. Ready to share."));
        lines.add(AttributedString.EMPTY);
    }

    private void appendReader(final List<AttributedString> lines, final String margin)
    {
        lines.add(line(focus == Focus.READER && !active ? ACCENT : MUTED, margin + "01  READER"));
        final String reader =
                readers.isEmpty() ? "No reader connected" : clean(readers.get(readerIndex));
        lines.add(
                new AttributedStringBuilder()
                        .append(margin)
                        .style(readers.isEmpty() ? ERROR : SUCCESS)
                        .append(readers.isEmpty() ? "○  " : "●  ")
                        .style(TEXT)
                        .append(reader)
                        .style(MUTED)
                        .append(readers.size() > 1 ? "   ‹ / ›" : "")
                        .toAttributedString());
        lines.add(AttributedString.EMPTY);
    }

    private void appendDestination(
            final List<AttributedString> lines, final String margin, final int contentWidth)
    {
        lines.add(
                line(
                        focus == Focus.DESTINATION && !active ? ACCENT : MUTED,
                        margin + "02  DESTINATION"));
        final int visible = contentWidth - 4;
        final int start = Math.max(0, cursor - visible + 1);
        final String before = url.substring(start, cursor);
        final String after = url.substring(cursor);
        final AttributedStringBuilder input =
                new AttributedStringBuilder().append(margin).style(ACCENT).append("› ");
        if (url.isEmpty() && (focus != Focus.DESTINATION || active))
        {
            input.style(MUTED).append("https://your-link.com");
        }
        else
        {
            input.style(TEXT).append(before);
            if (focus == Focus.DESTINATION && !active && !closing)
            {
                input.style(ACCENT.inverse()).append(after.isEmpty() ? " " : after.substring(0, 1));
                input.style(TEXT).append(after.isEmpty() ? "" : after.substring(1));
            }
            else
            {
                input.style(TEXT).append(after);
            }
        }
        lines.add(input.toAttributedString());
        lines.add(
                line(
                        MUTED,
                        margin
                                + (active
                                        ? "Destination locked for the current batch"
                                        : "Type or paste an HTTPS link")));
        lines.add(AttributedString.EMPTY);
    }

    private void appendStatus(
            final List<AttributedString> lines,
            final String margin,
            final int contentWidth,
            final long tick)
    {
        final AttributedStyle stateColor =
                state == ReaderStatus.State.ERROR
                        ? ERROR
                        : state == ReaderStatus.State.SUCCESS ? SUCCESS : ACCENT;
        final String label = statusLabel(tick);
        lines.add(
                new AttributedStringBuilder()
                        .append(margin)
                        .style(stateColor.bold())
                        .append(label)
                        .style(MUTED)
                        .append("    ·    ")
                        .style(TEXT.bold())
                        .append(Integer.toString(successfulCards))
                        .style(MUTED)
                        .append(" cards verified")
                        .toAttributedString());
        final AttributedString status = line(TEXT, message);
        final List<AttributedString> wrapped = status.columnSplitLength(contentWidth);
        lines.add(line(TEXT, margin + (wrapped.isEmpty() ? "" : wrapped.getFirst().toString())));
        lines.add(line(TEXT, margin + (wrapped.size() > 1 ? wrapped.get(1).toString() : "")));
        lines.add(AttributedString.EMPTY);
    }

    private String statusLabel(final long tick)
    {
        if (closing)
        {
            return "EXITING";
        }
        return switch (state)
        {
            case WRITING -> "WRITING " + SPINNER_FRAMES.charAt((int) (tick % SPINNER_FRAMES.length()));
            case SUCCESS -> "VERIFIED";
            case ERROR -> "ERROR";
            case ACTIVE -> "WAITING FOR CARD";
            case STOPPING -> "STOPPING";
            default -> "READY";
        };
    }

    private void appendActions(final List<AttributedString> lines, final String margin)
    {
        lines.add(
                new AttributedStringBuilder()
                        .append(margin)
                        .style(focus == Focus.ACTION ? ACCENT.inverse() : MUTED.inverse())
                        .append(" enter ")
                        .style(TEXT)
                        .append(active ? " Stop batch" : " Start batch")
                        .style(MUTED)
                        .append("     tab  Switch     esc  Exit")
                        .toAttributedString());
        lines.add(AttributedString.EMPTY);
    }

    private void appendActivity(final List<AttributedString> lines, final String margin)
    {
        lines.add(line(MUTED, margin + "ACTIVITY"));
        int shown = 0;
        for (final String event : history)
        {
            if (shown++ == VISIBLE_EVENTS)
            {
                break;
            }
            lines.add(line(MUTED, margin + event));
        }
    }

    private static AttributedString line(final AttributedStyle style, final String text)
    {
        return new AttributedString(text, style);
    }

    private static List<AttributedString> fit(
            final List<AttributedString> lines, final int width, final int height)
    {
        // JLine retains and mutates frames during scrolling and full redraws.
        return lines.stream()
                .limit(Math.max(1, height))
                .map(value -> value.columnSubSequence(0, Math.max(1, width - 1)))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static String clean(final String text)
    {
        return text.replaceAll("[\\p{Cntrl}\\p{Cf}]", "");
    }
}
