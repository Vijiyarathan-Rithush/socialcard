# SocialCard Terminal UI

Requires JDK 25, Maven, a PC/SC reader and a supported NTAG215 card.

```sh
mvn compile exec:java
```

Select a reader, enter a valid HTTPS URL and choose **Schreiben aktivieren**.
Place cards on the reader one at a time. Each card is written and verified;
remove it before presenting the next one. The same link is used throughout
this series. **Stoppen** disables the next write and allows a current write
to finish. Stop before changing the URL or reader.

Use Tab to move between controls, Enter to select and Esc to exit. Exiting
waits for an active write to finish. Connect a missing reader and choose
**Reader aktualisieren**. After a reader failure, reconnect it and activate
the job again. Logs are written to `logs/socialcard.log` and retained for seven days.

Lanterna uses a terminal emulator window on Windows and in graphical IDE
sessions. On supported text terminals it renders in the terminal itself.
A terminal area of at least 80 columns by 24 rows is recommended.

The existing `encodeURIs` API remains available; the first TUI accepts a
single HTTPS URL per series. Card capacity assumptions remain NTAG215.

## Structure

- `ConsoleApplication`: terminal lifecycle, reader selection and monitoring.
- `MainWindow`: form, controls, status, counter and the last 100 events.
- `ReaderListener`: active message, serial writes and status events.
- `IReaderStatusListener` and `ReaderStatus`: hardware-to-presentation notifications.
- `ReaderMonitor`: background card detection and graceful shutdown.

The monitor performs card operations sequentially. UI updates are dispatched
to Lanterna's GUI thread. Stopping clears the active message atomically and
allows an already reserved write to complete. Activation locks reader and URL
controls until the series has stopped. Existing encoder, repository and service
APIs remain unchanged.
