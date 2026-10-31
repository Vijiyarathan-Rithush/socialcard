<div align="center">

# SocialCard

**One link. One tap. Ready to share.**

A JavaFX desktop application for writing self-contained digital profiles or HTTPS links on NFC cards.

![Java](https://img.shields.io/badge/Java-25-111111?style=for-the-badge)
![JavaFX](https://img.shields.io/badge/JavaFX-25-111111?style=for-the-badge)
![Maven](https://img.shields.io/badge/Maven-111111?style=for-the-badge&logo=apachemaven&logoColor=white)
![NFC](https://img.shields.io/badge/NFC-NDEF-111111?style=for-the-badge&logo=nfc&logoColor=white)

[Getting started](#getting-started) · [Usage](#usage) · [Architecture](#architecture) · [Tests](#tests)

</div>

## Overview

SocialCard turns writable NFC cards into shareable profiles. In profile mode, your name, description and labelled HTTPS links are stored inside the URL fragment on the card. A static website displays the profile without a profile API or database. Direct URL mode is still available. Select a reader, prepare a profile or enter an HTTPS URL and write cards through a JavaFX interface. Every write is verified by reading the data back before the card is counted as successful.

Developed as a learning project during my software development apprenticeship.

## Features

- Profile editor with name, optional description and multiple labelled HTTPS links
- Static profile viewer in `docs/`, with no profile backend or database
- NDEF capacity validation before writing profiles
- JavaFX desktop interface with FXML views
- Reader selection and refresh
- Continuous batch writing with automatic card detection
- Write verification and a counter for each batch
- Contextual waiting, writing, verification and error views
- Expandable activity log with timestamps
- Graceful shutdown after the current write finishes
- Background hardware operations that keep the interface responsive
- Service API for encoding, reading and writing NDEF messages

## Getting started

### Requirements

| Requirement | Details |
| --- | --- |
| Java | JDK 25 |
| Build tool | Maven |
| NFC reader | Compatible PC/SC reader, such as the ACS ACR122U |
| System setup | Reader drivers and a working PC/SC service |
| NFC card | Writable NTAG215 using user pages 4–129 |

### Installation

```sh
git clone https://github.com/Vijiyarathan-Rithush/socialcard.git
cd socialcard
mvn clean test
mvn javafx:run
```

Set `JAVA_HOME` to your JDK 25 installation. Maven downloads the JavaFX dependencies; the application opens in a separate desktop window.

## Usage

1. Host the static viewer (see below), then connect your NFC reader and click **Refresh**.
2. Select the reader. Leave **Store a profile on the card** enabled.
3. Enter the HTTPS address where you hosted the viewer, your name, optional description and links.
4. Keep the profile within the displayed supported NDEF size. Shorter addresses leave more room for data.
5. Click **Write cards**, place a card on the reader and wait for verification.
6. Remove it and present another card to write the same profile, or click **Stop writing** to edit.
7. Tap the card with a compatible phone to open the profile in its browser.

Disable **Store a profile on the card** to write a plain HTTPS destination instead.

The reader and destination stay locked while a batch is active. The verified-card counter resets for each batch. Closing the window waits for an active write to finish.

**Writing replaces the card’s existing NDEF content.**

Open **Activity** to view the latest 100 timestamped events. The window expands to show the log. Application logs are saved to `logs/socialcard.log`.

## Architecture

The controller handles UI events and delegates operations to `IBatchService`. Reader discovery, batch lifecycle and hardware coordination remain behind the service interface. FXML defines the view separately from the controller.

| Package / location | Responsibility |
| --- | --- |
| `com.rvi` | Application startup and dependency wiring |
| `com.rvi.presentation` | Controllers only; project dependencies limited to the service layer |
| `com.rvi.service` | Batch lifecycle, reader discovery and card services |
| `com.rvi.application` | NDEF encoding, card monitoring and APDU access |
| `com.rvi.domain` | NDEF message representation and validation |
| `src/main/resources/com/rvi` | FXML views |

Hardware operations run outside the JavaFX application thread. Status updates are dispatched to the UI thread. NDEF messages validate their encoded structure and protect their data through defensive copies.

### Technology

| Component | Technology |
| --- | --- |
| Language | Java 25 |
| User interface | JavaFX 25 and FXML |
| Build | Maven |
| Reader communication | Java Smart Card I/O and APDU commands |
| Card format | NDEF URI records |
| Logging | SLF4J and Logback |
| Tests | JUnit Jupiter |

## Tests

```sh
mvn test
```

Tests cover NDEF encoding and validation, repository operations, reader services, card monitoring, listener recovery and JavaFX view loading. The JavaFX test requires a graphical desktop environment.

End-to-end writing and reader compatibility must also be checked with a physical NFC reader and card.

## Supported scope

| Area | Support |
| --- | --- |
| URL scheme | HTTPS |
| Desktop workflow | One profile URL or direct HTTPS URL per writing session |
| Service API | Single or multiple URI records; card reading and writing |
| NDEF payload | Up to 254 bytes of combined record data |
| Card layout | NTAG215 user pages 4–129 |
| Extended-length TLVs | Unsupported |

How a receiving device handles multiple URI records depends on its operating system and app.

## Author

**Rithush Vijiyarathan**

[![GitHub](https://img.shields.io/badge/GitHub-111111?style=for-the-badge&logo=github&logoColor=white)](https://github.com/Vijiyarathan-Rithush)


## Static profile viewer

The `docs/` directory contains a buildless, public-facing viewer. Serve `index.html`,
`style.css` and `viewer.mjs` together over HTTPS. No API, database, login or build step is required.
For example, you can configure GitHub Pages to serve `/docs` from the branch containing these files.
Hosting is not enabled by this code change. Use your actual deployed viewer URL in the desktop app;
a custom domain is optional. A private page requiring login is unsuitable for general card recipients.

For local browser development, run `python -m http.server 8080 --directory docs` and open
`http://localhost:8080/#v=1&n=Rithush&l=GitHub&u=https%3A%2F%2Fgithub.com%2Frvi`.
Local HTTP is for development only; the desktop writer requires an HTTPS viewer address.

Run viewer parsing tests with `node --test docs/viewer.test.mjs`.
The Java profile encoder tests run with the existing `mvn test` suite.

### Data format and limits

Version 1 uses a URL fragment containing form-encoded parameters:
`#v=1&n=Name&b=Description&l=Label&u=https%3A%2F%2Fexample.com`.
`b` is optional; repeat paired `l` and `u` fields in display order for more links.
The Java writer percent-encodes UTF-8 values; the viewer decodes them with `URLSearchParams`.

The card contains the complete profile URL, not a database identifier. Changing profile content
requires rewriting the card. Copies of old URLs keep their old content. Initial viewer loading
needs internet access. The current encoder supports 254 bytes of NDEF record data (257 bytes
including its TLV and terminator), even though NTAG215 has more physical user memory.
Percent encoding, the viewer address and field names consume this budget. Up to ten link rows
are allowed, but the byte limit is usually reached much sooner. Images are not supported.
Extended-length NDEF support is a separate future change.

Fragments are normally not sent in the HTTP request. The viewer has no analytics, third-party
scripts or profile uploads, and renders fields as plain text. Nevertheless, anyone who can read
the card or obtain its URL can read and modify the profile data. It is unsigned public content,
not proof of identity. Do not store secrets. Link destinations are restricted to HTTPS and links
open without sending a referrer. The host must preserve the fragment and serve module scripts
with a JavaScript MIME type.
