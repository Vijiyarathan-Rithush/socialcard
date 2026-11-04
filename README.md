<div align="center">

# SocialCard

A Java 25 library for creating, reading and writing NDEF messages on NTAG215 tags through a compatible PC/SC reader.

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
- Write a link with `writeURI()` and read links as strings with `readURIs()`.
- Decode supported records into typed content with `readContents()` or `NDefDecoder`.
- Discover readers and monitor card insertion and removal.
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

SocialCard `0.1.1` is available on Maven Central. Add the dependency to your
application's `pom.xml`; no local build or custom Maven repository is needed.

```xml
<dependency>
    <groupId>io.github.vijiyarathan-rithush</groupId>
    <artifactId>socialcard</artifactId>
    <version>0.1.1</version>
</dependency>
```

SocialCard uses the SLF4J API. Applications choose their own logging implementation.

## Quick start

Place a card on the reader before running this example:

```java
import com.rvi.service.IReaderService;
import com.rvi.service.ReaderService;

public final class SocialCardExample
{
    public static void main(final String[] args)
    {
        try (final IReaderService service = ReaderService.create())
        {
            service.connect();
            service.writeURI("https://rithush.dev");
            System.out.println(service.readURIs());
        }
    }
}
```

`ReaderService.create()` selects the first reader. Use
`ReaderService.create("Exact Reader Name")` to select a reader by name.
Creating a service does not open a connection; `connect()` does.
Use `ReaderService.listReaders()` to get exact reader names without a card.

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
The convenience methods `encodeURI()` and `encodeURIs()` accept the same absolute
URIs as `NDefUriCreator`, including HTTP, HTTPS, `tel:` and `mailto:`.
Include the scheme: use `https://www.example.com`, not `www.example.com`.

## Read usable content

```java
// With an already connected service:
final var links = service.readURIs(); // immutable List<String>
final var values = service.readContents(); // immutable List<NDefContent>

// Without hardware, decode an existing NDefMessage:
final var contents = new com.rvi.application.NDefDecoder().decode(message);
```

`read()` still returns the original validated `NDefMessage`. Typed decoding
returns `NDefUri`, `NDefText`, `NDefContact` or `NDefWifi` in record order.
vCard decoding supports SocialCard's vCard 4.0 fields (structured first/last name,
email, text phone and website); other or repeated properties are rejected.
Wi-Fi decoding supports one open network or WPA2-Personal/AES credential.
This is not a general-purpose vCard or WSC parser.

## Wait for a card

`connect()` does not wait for insertion. Use `ReaderMonitor` with your own
`IReaderListener` to react when a card is placed on or removed from a reader.
This console example keeps monitoring until Enter is pressed:

```java
import com.rvi.application.IReaderListener;
import com.rvi.application.ReaderMonitor;
import com.rvi.service.ReaderService;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.TerminalFactory;

public final class MonitorExample
{
    public static void main(final String[] args) throws Exception
    {
        final var names = ReaderService.listReaders();
        if (names.isEmpty())
        {
            System.out.println("Connect a PC/SC reader first.");
            return;
        }

        final String readerName = names.getFirst();
        final CardTerminal terminal =
                TerminalFactory.getDefault().terminals().getTerminal(readerName);
        if (terminal == null)
        {
            throw new IllegalStateException("Reader disconnected: " + readerName);
        }

        final IReaderListener listener = new IReaderListener()
        {
            @Override
            public void onCardInserted(final CardTerminal reader)
            {
                System.out.println("Card detected on " + reader.getName());
            }

            @Override
            public void onCardRemoved(final CardTerminal reader)
            {
                System.out.println("Card removed");
            }

            @Override
            public void onError(final Exception exception)
            {
                System.err.println(exception.getMessage());
            }
        };

        try (final ReaderMonitor monitor = new ReaderMonitor(terminal, listener))
        {
            monitor.start();
            System.out.println("Place a card on the reader. Press Enter to stop.");
            System.in.read();
        }
    }
}
```

Monitoring detects presence; it does not open a card connection. When writing,
use `ReaderService.create(readerName)` with the same reader name. The card can
be removed between detection and writing, so still handle write failures.

Callbacks run on the monitor's background thread. A polling or callback failure
is forwarded to `onError()` while active and ends monitoring. A monitor can
start only once; `close()` requests a stop, but an in-flight callback may finish.
Create a new monitor to restart.

## Use in a JavaFX application

Keep the GUI in a separate Maven project and add the dependency above. JavaFX
is supplied by your application; SocialCard has no JavaFX dependency.

- Populate the reader selector with `ReaderService.listReaders()` in a background task.
- Start a monitor for the selected reader and implement `IReaderListener`.
- Dispatch listener-driven UI updates with `Platform.runLater(...)`.
- Run connection, reading and writing in a JavaFX `Task` or background executor.
- Enable the write button only while a card is present and no write is running.
- Close the old monitor when switching readers and close the active monitor when
  the window closes. Each write can own its service in a try-with-resources block.

Your GUI manages input, status and application lifecycle. SocialCard handles
encoding, capacity checks, card communication and write verification.

## Errors and limitations

- `ReaderServiceException` reports connection and card communication failures.
- `CardCapacityException` reports messages that exceed the available card memory.
- `NDefValueException`, `NDefEncodingException`, `NDefMessageException` and
  `TlvException` report invalid values or unsupported message structures.
- Card access uses the NTAG215 memory layout. Capacity comes from the capability
  container, which advertises 496 bytes on a factory-configured NTAG215.
- The supported layout starts with an NDEF TLV and ends with a terminator.
  Preceding control TLVs, chunked content and record IDs are not supported.
- Raw reads validate NDEF structure. Typed reads additionally validate the supported payload formats.
- Writing replaces the current NDEF message. Removing the card during a write can
  leave incomplete content; writing is not an atomic operation.

## Troubleshooting

| Symptom | What to check |
|---------|---------------|
| Build fails with unsupported release 25 | Run `java -version` and `mvn -version`; both must use JDK 25 or later. Set your IDE project SDK too. |
| No reader found / empty reader list | Connect the USB reader, install its PC/SC driver and check the OS smart-card service (Windows Smart Card service; Linux pcscd). |
| No card present | Place a supported tag on the reader before `connect()`. The method does not wait. |
| Service is not connected | Call `connect()` inside the try-with-resources block before reading or writing. |
| Capacity exceeded | Reduce content. NTAG215 advertises 496 bytes shared by NDEF, TLV overhead and terminator. |
| Invalid URI | Include a scheme such as `https://`; encode spaces as `%20`. |
| Unsupported record or layout | Use the supported NTAG215 layout and record types listed above; PC/SC alone does not imply support for every NFC tag. |

Hardware access is exercised with test doubles in automated tests. The reported
ACR122U/NTAG215 setup is the project's reference hardware, not a compatibility
guarantee for other readers or tags. Run a real write/read test with your hardware.

## Build from source

```bash
git clone https://github.com/Vijiyarathan-Rithush/socialcard.git
cd socialcard
mvn clean verify
```

Use JDK 25. `com.rvi.Main` contains a runnable reader example.

## License

[MIT](LICENSE) · [Rithush Vijiyarathan](https://github.com/Vijiyarathan-Rithush)
