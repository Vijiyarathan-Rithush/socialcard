<div align="center">

# SocialCard

Store your links on an NFC card.

A Java application for creating, reading and writing NDEF messages on NFC tags.

![Java](https://img.shields.io/badge/Java-25-111111?style=for-the-badge)
![Maven](https://img.shields.io/badge/Maven-111111?style=for-the-badge&logo=apachemaven&logoColor=white)
![NFC](https://img.shields.io/badge/NFC-NDEF-111111?style=for-the-badge&logo=nfc&logoColor=white)
![Status](https://img.shields.io/badge/Status-In_Development-111111?style=for-the-badge)

</div>

## Overview

SocialCard stores URIs, text, contacts and Wi-Fi credentials on writable NFC tags.
It communicates with an NFC reader through Java Smart Card I/O and
uses APDU commands to access tag memory.

Developed as a learning project during my software development apprenticeship.

## Features

- Create URI, UTF-8 text, vCard 4.0 contact and Wi-Fi WSC content with separate fluent Creator classes
- Combine different content types in one NDEF message
- Keep the existing HTTPS encoding methods for compatibility
- Read supported NDEF messages from a tag
- Write messages using four-byte memory pages
- Verify written data by reading it back
- Validate URL format, message size and NDEF record structure
- Support short and long NDEF URI records and extended-length NDEF TLVs
- Read the NDEF area size and access permissions from the capability container

## Technology

| Component | Technology |
|-----------|------------|
| Language | Java 25 |
| Build tool | Maven |
| Reader communication | Java Smart Card I/O |
| Commands | APDU |
| Data format | NDEF URI, Text and MIME content |
| Logging | SLF4J API; applications choose their logging implementation |

## Requirements

- JDK 25
- Maven
- A compatible PC/SC NFC reader, such as the ACS ACR122U
- Installed reader drivers and a working PC/SC service
- A writable NTAG215 tag matching the configured memory layout

## Release preparation

The prepared first Maven Central release is `io.github.vijiyarathan-rithush:socialcard:0.1.0`.
SocialCard is licensed under MIT. It is not published on Maven Central yet; the publishing namespace must be verified before release.
See [RELEASING.md](RELEASING.md) for preview builds, signing and publishing instructions.

## Getting Started

### Clone the repository

```bash
git clone https://github.com/Vijiyarathan-Rithush/socialcard.git
cd socialcard
```

### Compile

```bash
mvn clean compile
```

### Configure your links

Edit `createMessage()` in `src/main/java/com/rvi/Main.java`:

```java
final NDefUri website = new NDefUriCreator().uri("https://rithush.dev").build();
final NDefText text = new NDefTextCreator().text("Hello Rithush").language("en").build();
final NDefMessage message = new NDefMessageCreator().add(website).add(text).build();
```

### Run

1. Open the project as a Maven project in your IDE.
2. Select JDK 25.
3. Connect the NFC reader.
4. Place a writable tag on the reader.
5. Run `com.rvi.Main`.

The application selects the first available reader and writes the
configured message to the tag. Writing replaces the existing NDEF content.

## API Examples

### Create typed content

Domain objects are immutable and validate their values even when constructed directly.
Each fluent builder is a separate class in `com.rvi.application.creator`.
`build()` creates a value; `NDefMessageCreator` preserves the order of added values.

```java
final NDefUri phone = new NDefUriCreator().uri("tel:+41791234567").build();
final NDefText text = new NDefTextCreator().text("Hello Rithush").language("en").build();
final NDefContact contact = new NDefContactCreator().firstName("Rithush").lastName("Vijiyarathan").email("hello@rithush.dev").phone("+41791234567").website("https://rithush.dev").build();
final NDefWifi wifi = new NDefWifiCreator().ssid("Guest").authentication(WifiAuthentication.WPA2_PERSONAL).encryption(WifiEncryption.AES).password("example-password").build();
final NDefMessage message = new NDefMessageCreator().add(phone).add(text).add(contact).build();
final NDefMessage wifiMessage = new NDefMessageCreator().add(wifi).build();
```

`NDefTextCreator` defaults to language `en`. `NDefWifiCreator` defaults to WPA2-Personal
with AES; open networks require `OPEN` and `NONE` without a password.
Support for automatically acting on a tag depends on the phone and reading app.
Wi-Fi credentials are stored on the tag as readable data.

### Encode links without a reader

`NDefEncoder` creates NDEF messages independently of NFC hardware:

```java
final NDefEncoder encoder = new NDefEncoder();

final NDefMessage single = encoder.encodeURI("https://github.com/Vijiyarathan-Rithush");

final NDefMessage multiple = encoder.encodeURIs(List.of("https://github.com/Vijiyarathan-Rithush", "https://www.youtube.com/"));
```

### Connect, write and read through the service

Place a card on the reader before calling `connect()`. The service selects the first
reader by default, connects to the card and creates the repository internally.
No terminal, channel, listener, monitor or scanner is needed for a one-shot operation.

```java
try (final IReaderService service = ReaderService.create())
{
    service.connect();
    service.write(message);

    final NDefMessage stored = service.read();
}
```

The factory methods are `ReaderService.create()` for the first reader and
`ReaderService.create("Exact Reader Name")` for a named reader. Creating a service
does not contact hardware. `new ReaderService()` is also supported.

`connect()` does not wait for a card: a missing reader or card raises
`ReaderServiceException`. Calling `connect()` twice without disconnecting also fails.
`disconnect()` releases an owned card connection and can be called repeatedly.
`close()` calls `disconnect()` automatically, including when the body throws.
After disconnecting, the service can connect again.

You can also manage the lifetime explicitly:

```java
final IReaderService service = ReaderService.create();

try
{
    service.connect();
    service.write(message);
}
finally
{
    service.disconnect();
}
```

The existing repository-taking constructor remains available for externally managed
connections, including `ReaderListener`. In that mode the caller owns the connection:
`connect()` is unavailable, while `disconnect()` and `close()` do not close it.
The monitor remains optional for applications that react to card insertion and removal.

### Existing HTTPS methods

With a connected `IReaderService`:

```java
final NDefMessage message = service.encodeURIs(List.of("https://github.com/Vijiyarathan-Rithush", "https://www.youtube.com/"));

service.write(message);

final NDefMessage stored = service.read();
```

## Project Structure

| Package | Responsibility |
|---------|----------------|
| `com.rvi` | Application entry point and example configuration |
| `com.rvi.application` | NDEF encoding, reader repository and APDU communication |
| `com.rvi.application.exception` | Encoding, TLV and reader exceptions |
| `com.rvi.application.creator` | Separate fluent builders for values and messages |
| `com.rvi.service` | Service factories, connection lifetime, encoding and tag access |
| `com.rvi.domain` | NDEF message representation and validation |
| `com.rvi.domain.exception` | NDEF message validation exceptions |

`NDefEncoder` handles URL encoding, while `ReaderRepository` handles
communication with the NFC tag. `ReaderService` provides access to both.

`NDefMessage` validates the message structure and protects its internal
byte array through defensive copies. Its `data()` contains only NDEF bytes,
without a TLV header, terminator or page padding. `TlvEncoder` adds and `TlvDecoder` removes
the TLV wrapper; `ReaderRepository` applies page padding only when writing.

Repository communication errors use `ReaderServiceException`, preserving
the original cause. Capacity errors use `CardCapacityException`, and TLV
format errors use `TlvException`. `IReaderRepository` retains checked
`CardException` declarations. The service API wraps checked card failures in
`ReaderServiceException` and preserves the original cause.

## Current Limitations

- Creator-based messages support absolute URIs, UTF-8 text, vCard 4.0 contacts and open or WPA2-Personal/AES Wi-Fi
- Legacy `encodeURI` and `encodeURIs` methods remain HTTPS-only
- Reading returns validated NDEF bytes; conversion back into typed domain objects is not implemented
- Only the SocialCard layout is supported: NDEF TLV at the start of user memory, followed by a terminator
- Record IDs, chunked records and preceding control TLVs are not supported
- Physical memory access remains configured for NTAG215 pages 4 through 129
- The capability container limits the NDEF area; a factory NTAG215 advertises 496 bytes, including TLV overhead
- Writing is not atomic; removing the tag during a write can leave incomplete content
- Reader selection and message content are configured through the API
- Display and handling of multiple links depend on the receiving device and app

## Author

**Rithush Vijiyarathan**

[![GitHub](https://img.shields.io/badge/GitHub-111111?style=for-the-badge&logo=github&logoColor=white)](https://github.com/Vijiyarathan-Rithush)