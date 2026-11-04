<div align="center">

# SocialCard

A Java 25 library for creating, reading and writing NDEF messages on NFC tags.

![Java](https://img.shields.io/badge/Java-25-111111?style=for-the-badge)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.vijiyarathan-rithush/socialcard?style=for-the-badge&color=111111&label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.vijiyarathan-rithush/socialcard)
[![License](https://img.shields.io/badge/License-MIT-111111?style=for-the-badge)](LICENSE)

</div>

## What it does

SocialCard connects to an NFC card through a PC/SC reader and stores NDEF messages
containing links, text, contacts or Wi-Fi credentials. Use it as a Maven dependency
in a console application, desktop application or another Java project.

- Create immutable domain objects with separate fluent `Creator` classes.
- Combine different content types in one message, preserving their order.
- Read and write cards through a service with automatic connection cleanup.
- Encode short and long NDEF payloads and standard or extended TLV lengths.
- Check the card's advertised capacity and verify each write by reading it back.

## Requirements

- Java 25 or later.
- A compatible PC/SC NFC reader with its drivers and PC/SC service available.
- A writable NTAG215 using the supported memory layout.

Creating and encoding messages does not require hardware. Card access does.
The project has been tested with an ACS ACR122U reader and NTAG215 tags.

## Installation

Add the dependency to your Maven project's `pom.xml`:

```xml
<dependency>
    <groupId>io.github.vijiyarathan-rithush</groupId>
    <artifactId>socialcard</artifactId>
    <version>0.1.0</version>
</dependency>
```

SocialCard uses the SLF4J API. Applications choose their own logging implementation.

## Quick start

Place a card on the reader before running this example:

```java
import com.rvi.application.creator.NDefMessageCreator;
import com.rvi.application.creator.NDefUriCreator;
import com.rvi.domain.NDefMessage;
import com.rvi.domain.NDefUri;
import com.rvi.service.IReaderService;
import com.rvi.service.ReaderService;

public final class SocialCardExample
{
    public static void main(final String[] args)
    {
        final NDefUri website = new NDefUriCreator().uri("https://rithush.dev").build();
        final NDefMessage message = new NDefMessageCreator().add(website).build();

        try (final IReaderService service = ReaderService.create())
        {
            service.connect();
            service.write(message);

            final NDefMessage stored = service.read();
            System.out.println("Read %d NDEF bytes".formatted(stored.data().length));
        }
    }
}
```

`ReaderService.create()` selects the first reader. Use
`ReaderService.create("Exact Reader Name")` to select a reader by name.
Creating a service does not open a connection; `connect()` does.

`connect()` fails immediately if the reader or card is unavailable. `close()` calls
`disconnect()` automatically. You can also call `disconnect()` explicitly; repeated
calls are safe, and the service can reconnect afterward.

## Create content

```java
import com.rvi.application.creator.NDefContactCreator;
import com.rvi.application.creator.NDefMessageCreator;
import com.rvi.application.creator.NDefTextCreator;
import com.rvi.application.creator.NDefUriCreator;
import com.rvi.application.creator.NDefWifiCreator;
import com.rvi.domain.NDefContact;
import com.rvi.domain.NDefMessage;
import com.rvi.domain.NDefText;
import com.rvi.domain.NDefUri;
import com.rvi.domain.NDefWifi;
import com.rvi.domain.WifiAuthentication;
import com.rvi.domain.WifiEncryption;

final NDefUri website = new NDefUriCreator().uri("https://rithush.dev").build();
final NDefUri phone = new NDefUriCreator().uri("tel:+41791234567").build();
final NDefUri email = new NDefUriCreator().uri("mailto:hello@rithush.dev").build();
final NDefText text = new NDefTextCreator().text("Hello from SocialCard").language("en").build();
final NDefContact contact = new NDefContactCreator().firstName("Rithush").lastName("Vijiyarathan").email("hello@rithush.dev").phone("+41791234567").website("https://rithush.dev").build();
final NDefWifi wifi = new NDefWifiCreator().ssid("Guest").password("example-password").build();
final NDefWifi openWifi = new NDefWifiCreator().ssid("Open Guest").authentication(WifiAuthentication.OPEN).encryption(WifiEncryption.NONE).build();

final NDefMessage profile = new NDefMessageCreator().add(website).add(text).add(contact).build();
final NDefMessage network = new NDefMessageCreator().add(wifi).build();
```

| Content | Format | Defaults |
|---------|--------|----------|
| `NDefUri` | Absolute URI, including HTTPS, HTTP, `tel:` and `mailto:` | No default URI |
| `NDefText` | UTF-8 text with a language tag | Language `en` |
| `NDefContact` | vCard 4.0 | At least a first or last name is required |
| `NDefWifi` | Wi-Fi WSC credentials | WPA2-Personal with AES |

Creators collect values; `build()` creates an immutable object. Domain constructors
also validate their values. Wi-Fi credentials are stored as readable data on the tag.
Phone and application support determines what happens when the tag is scanned.

## Encoding without a reader

```java
import com.rvi.application.NDefEncoder;
import com.rvi.application.TlvDecoder;
import com.rvi.application.TlvEncoder;
import com.rvi.domain.NDefMessage;
import java.util.List;

final NDefEncoder encoder = new NDefEncoder();
final NDefMessage message = encoder.encodeURIs(List.of("https://rithush.dev", "https://github.com/Vijiyarathan-Rithush"));
final byte[] tagData = new TlvEncoder().encode(message);
final NDefMessage decoded = new TlvDecoder().decode(tagData);
```

`NDefMessage.data()` returns a defensive copy of the NDEF bytes, without the TLV
wrapper, terminator or page padding. `TlvEncoder` and `TlvDecoder` handle the wrapper.
The reader service applies this conversion automatically when accessing a card.
The convenience methods `encodeURI()` and `encodeURIs()` accept HTTPS URLs only;
use `NDefUriCreator` for other URI schemes.

## Errors and limitations

- `ReaderServiceException` reports connection and card communication failures.
- `CardCapacityException` reports messages that exceed the available card memory.
- `NDefValueException`, `NDefEncodingException`, `NDefMessageException` and
  `TlvException` report invalid values or unsupported message structures.
- Card access uses the NTAG215 memory layout. Capacity comes from the capability
  container, which advertises 496 bytes on a factory-configured NTAG215.
- The supported layout starts with an NDEF TLV and ends with a terminator.
  Preceding control TLVs, chunked content and record IDs are not supported.
- Reading returns validated NDEF bytes, not reconstructed typed domain objects.
- Writing replaces the current NDEF message. Removing the card during a write can
  leave incomplete content; writing is not an atomic operation.

## Build from source

```bash
git clone https://github.com/Vijiyarathan-Rithush/socialcard.git
cd socialcard
mvn clean verify
```

Use JDK 25. `com.rvi.Main` contains a runnable reader example.

## License

[MIT](LICENSE) · [Rithush Vijiyarathan](https://github.com/Vijiyarathan-Rithush)
