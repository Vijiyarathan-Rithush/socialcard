package com.rvi.presentation;

import com.rvi.application.ReaderStatus;
import org.jline.utils.AttributedString;
import org.jline.utils.Display;
import org.jline.terminal.Size;
import org.jline.terminal.impl.DumbTerminal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MainWindowTest
{
    @Test
    void rendersBorderlessViewWithReaderStatusAndCount()
    {
        final MainWindow window = new MainWindow();
        window.setReaders(List.of("ACS ACR122U"));
        window.insert("https://example.com");
        window.showStatus(new ReaderStatus(ReaderStatus.State.SUCCESS, "Karte verifiziert.", 3));
        final String rendered = plain(window, 80, 24);
        assertTrue(rendered.contains("ACS ACR122U"));
        assertTrue(rendered.contains("https://example.com"));
        assertTrue(rendered.contains("VERIFIZIERT"));
        assertTrue(rendered.contains("3 Karten verifiziert"));
        assertFalse(rendered.contains("┌"));
        assertFalse(rendered.contains("│"));
        assertTrue(window.render(80, 24, 0).stream().allMatch(line -> line.columnLength() < 80));
    }

    @Test
    void activeSeriesLocksUrlAndReaderSelection()
    {
        final MainWindow window = new MainWindow();
        window.setReaders(List.of("First", "Second"));
        window.insert("https://example.com");
        window.setActive(true);
        window.insert("oops");
        window.backspace();
        window.clearUrl();
        window.nextFocus();
        window.move(1);
        assertEquals("https://example.com", window.url());
        assertEquals(0, window.selectedReader());
        assertTrue(plain(window, 80, 24).contains("Serie stoppen"));
    }

    @Test
    void supportsEditingAndReaderCyclingAfterStopping()
    {
        final MainWindow window = new MainWindow();
        window.setReaders(List.of("First", "Second"));
        window.insert("https://example.com");
        window.move(-1);
        window.backspace();
        window.insert("o");
        assertEquals("https://example.com", window.url());
        window.nextFocus();
        window.nextFocus();
        window.move(1);
        assertEquals(1, window.selectedReader());
        window.setReaders(List.of("Second", "Third"));
        assertEquals(0, window.selectedReader());
    }

    @Test
    void stripsTerminalControlSequencesAndHandlesResize()
    {
        final MainWindow window = new MainWindow();
        window.insert("https://example.com\n\r\u0007");
        assertEquals("https://example.com", window.url());
        window.showMessage("Error\u001b\n", true);
        assertFalse(plain(window, 80, 24).contains("\u001b"));
        assertTrue(plain(window, 40, 10).contains("vergrössern"));
        assertTrue(window.render(40, 10, 0).stream().allMatch(line -> line.columnLength() < 40));
        assertTrue(plain(window, 80, 24).contains("FEHLER"));
    }

    @Test
    void jlineCanUpdateAndResizeRenderedFrames() throws Exception
    {
        final MainWindow window = new MainWindow();
        window.setReaders(List.of("ACS ACR122U"));
        try (DumbTerminal terminal = new DumbTerminal("test", "windows",
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), StandardCharsets.UTF_8))
        {
            final Display display = new Display(terminal, true);
            for (int height : new int[] {24, 30, 22, 10, 24})
            {
                terminal.setSize(new Size(80, height));
                display.resize(height, 80);
                for (int frame = 0; frame < 8; frame++)
                {
                    window.showMessage("Event " + frame, false);
                    if (frame == 4) { display.clear(); }
                    display.update(window.render(80, height, frame), 0);
                }
            }
        }
    }

    private static String plain(MainWindow window, int width, int height)
    {
        return String.join("\n", window.render(width, height, 0).stream().map(AttributedString::toString).toList());
    }
}
