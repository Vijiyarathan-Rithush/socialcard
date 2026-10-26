# SocialCard Terminal UI

A borderless interface running directly inside your terminal. Uses JLine 4
with its bundled JNI provider for native Windows, Linux and macOS terminal
access. No Swing window and no Cygwin or stty.exe installation is required.

Requires JDK 25, Maven, a PC/SC reader and a supported NTAG215 card. On
Windows, use PowerShell inside Windows Terminal for the intended rendering.
An IDE output panel is not an interactive terminal and is not supported.

```sh
mvn compile exec:java
```

## Controls

- Tab: switch between reader, URL and the primary action.
- Left/right on the reader: select a connected reader.
- Left/right in the URL: move the editing cursor.
- Backspace: delete the character before the cursor.
- Ctrl+U: clear the URL.
- Enter in the URL or action: start a series, or stop the active series.
- Enter on the reader: focus the URL.
- R outside the URL field: refresh connected readers while stopped.
- Esc or Ctrl+C: finish an active write and exit.

Paste a valid HTTPS URL, then press Enter. Place cards on the reader one at
a time. Each card is written and verified; remove it before presenting the
next one. The link remains locked during a series. Stopping disables further
writes and allows the current write to complete.

All interface labels and status messages are in English.

The view includes the selected reader, destination, live state, verified-card
counter and the three most recent events. It keeps 100 events in memory.
The layout adapts to terminal size and shows a resize hint below 60 columns
by 22 rows. The background follows your terminal theme; use a dark theme for
the intended muted text and violet accent. Runtime logs go to
`logs/socialcard.log` and are retained for seven days.

Connect a missing reader and refresh it. After a reader failure, reconnect
it and activate the job again. The existing `encodeURIs` API remains
available; the TUI accepts a single HTTPS URL per series. Card capacity
assumptions remain NTAG215.

## Structure

- `ConsoleApplication`: native terminal lifecycle, key bindings, status queue
  and reader monitoring.
- `MainWindow`: input model and styled borderless rendering.
- `ReaderListener`: active message, serial writes and status events.
- `IReaderStatusListener` and `ReaderStatus`: worker-to-presentation notifications.
- `ReaderMonitor`: background card detection and graceful shutdown.

The monitor performs card operations sequentially. The terminal event loop
consumes worker notifications; the worker never draws or edits the view.
Stopping clears the active message atomically. Exiting restores terminal
attributes, cursor visibility and the original screen, including on errors.
