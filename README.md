<div align="center">

# SocialCard

**One link. One tap. Ready to share.**

A JavaFX desktop application for writing and verifying HTTPS links on NFC cards.

![Java](https://img.shields.io/badge/Java-25-111111?style=for-the-badge)
![JavaFX](https://img.shields.io/badge/JavaFX-25-111111?style=for-the-badge)
![Maven](https://img.shields.io/badge/Maven-111111?style=for-the-badge&logo=apachemaven&logoColor=white)
![NFC](https://img.shields.io/badge/NFC-NDEF-111111?style=for-the-badge&logo=nfc&logoColor=white)

[Getting started](#getting-started) · [Usage](#usage) · [Architecture](#architecture) · [Tests](#tests)

</div>

## Overview

SocialCard turns writable NFC cards into shareable links. Select a reader, enter an HTTPS URL and prepare a batch of cards through a JavaFX interface. Every write is verified by reading the data back before the card is counted as successful.

Developed as a learning project during my software development apprenticeship.

## Features

- JavaFX desktop interface with FXML views
- Reader selection and refresh
- Continuous batch writing with automatic card detection
- Write verification and a counter for each batch
- Live status updates and recent activity
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

1. Connect your NFC reader and click **Refresh**.
2. Select the reader and enter a valid HTTPS URL.
3. Click **Start batch**.
4. Place a card on the reader and wait for confirmation that it was written and verified.
5. Remove the card and present the next one.
6. Click **Stop batch** before changing the reader or destination.

The reader and destination stay locked while a batch is active. The verified-card counter resets for each batch. Closing the window waits for an active write to finish.

**Writing replaces the card’s existing NDEF content.**

The activity list retains the latest 100 events. Application logs are saved to `logs/socialcard.log`.

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
| Desktop workflow | One link per batch |
| Service API | Single or multiple URI records; card reading and writing |
| NDEF payload | Up to 254 bytes of combined record data |
| Card layout | NTAG215 user pages 4–129 |
| Extended-length TLVs | Unsupported |

How a receiving device handles multiple URI records depends on its operating system and app.

## Author

**Rithush Vijiyarathan**

[![GitHub](https://img.shields.io/badge/GitHub-111111?style=for-the-badge&logo=github&logoColor=white)](https://github.com/Vijiyarathan-Rithush)
