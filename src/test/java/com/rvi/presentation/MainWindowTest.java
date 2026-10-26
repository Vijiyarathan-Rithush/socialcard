package com.rvi.presentation;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import com.rvi.application.ReaderStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class MainWindowTest
{
    @Test
    void rendersFormStatusAndCounterAndHandlesEscape() throws Exception
    {
        final DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(new TerminalSize(80, 24));
        try (TerminalScreen screen = new TerminalScreen(terminal))
        {
            screen.startScreen();
            final MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);
            gui.setTheme(ConsoleApplication.createTheme());
            final AtomicBoolean exit = new AtomicBoolean();
            final MainWindow window = new MainWindow(() -> {}, () -> {}, () -> {}, () -> exit.set(true));
            window.setReaders(List.of("ACS ACR122U"));
            window.showStatus(new ReaderStatus(ReaderStatus.State.SUCCESS, "Karte verifiziert.", 3));
            gui.addWindow(window);
            gui.updateScreen();
            final StringBuilder rendered = new StringBuilder();
            for (int row = 0; row < 24; row++)
            {
                for (int column = 0; column < 80; column++)
                {
                    rendered.append(screen.getFrontCharacter(column, row).getCharacterString());
                }
                rendered.append('\n');
            }
            assertTrue(rendered.toString().contains("ACS ACR122U"), rendered.toString());
            assertTrue(rendered.toString().contains("Karte verifiziert."), rendered.toString());
            assertTrue(rendered.toString().contains("Erfolgreich beschrieben: 3"), rendered.toString());
            assertTrue(rendered.toString().contains("Beenden"));
            window.handleInput(new KeyStroke(KeyType.Escape));
            assertTrue(exit.get());
        }
    }
}
