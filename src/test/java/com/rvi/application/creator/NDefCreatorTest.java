package com.rvi.application.creator;

import com.rvi.application.exception.NDefEncodingException;
import com.rvi.domain.NDefContact;
import com.rvi.domain.NDefMessage;
import com.rvi.domain.NDefText;
import com.rvi.domain.NDefUri;
import com.rvi.domain.NDefWifi;
import com.rvi.domain.WifiAuthentication;
import com.rvi.domain.WifiEncryption;
import com.rvi.domain.exception.NDefValueException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

final class NDefCreatorTest
{
    @Test
    void buildsSeparateImmutableValues()
    {
        final NDefTextCreator creator = new NDefTextCreator().text("Hallo");
        final NDefText first = creator.build();
        final NDefText second = creator.text("Hello").language("en").build();

        assertEquals(new NDefText("Hallo", "en"), first);
        assertEquals(new NDefText("Hello", "en"), second);
    }

    @Test
    void validatesDirectConstructionAsWellAsCreators()
    {
        assertThrows(NDefValueException.class, () -> new NDefText(null, "de"));
        assertThrows(NDefValueException.class, () -> new NDefTextCreator().build());
        assertThrows(NDefValueException.class, () -> new NDefUri("relative/path"));
        assertThrows(NDefValueException.class, () -> new NDefUriCreator().uri("relative/path").build());
        assertThrows(NDefValueException.class, () -> new NDefContact(null, null, null, null, null));
        assertThrows(NDefValueException.class, () -> new NDefContactCreator().build());
        assertThrows(NDefValueException.class, () -> new NDefWifiCreator().build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://rithush.dev", "http://example.org", "tel:+41791234567", "mailto:hello@rithush.dev", "urn:example:test"})
    void acceptsAbsoluteUris(final String uri)
    {
        assertEquals(uri, new NDefUriCreator().uri(uri).build().uri());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "https:///path", "https://user:pass@example.org", "https://a.ch/a b", "https://a.ch/%ZZ", "mailto:"})
    void rejectsInvalidUris(final String uri)
    {
        assertThrows(NDefValueException.class, () -> new NDefUriCreator().uri(uri).build());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"de_DE", "ä", "-de", "de-", "de-123456789"})
    void rejectsInvalidLanguages(final String language)
    {
        assertThrows(NDefValueException.class, () -> new NDefTextCreator().text("Hallo").language(language).build());
    }

    @Test
    void rejectsLanguagesLongerThanTheSixBitLengthField()
    {
        assertThrows(NDefValueException.class, () -> new NDefTextCreator().text("Hallo").language("a".repeat(64)).build());
    }

    @Test
    void buildsMinimalAndCompleteContacts()
    {
        final NDefContact minimal = new NDefContactCreator().firstName("Rithush").build();
        final NDefContact complete = new NDefContactCreator().firstName("Rithush").lastName("Vijiyarathan").email("hello@rithush.dev").phone("+41791234567").website("https://rithush.dev").build();

        assertEquals("", minimal.email());
        assertEquals("", minimal.website());
        assertEquals("hello@rithush.dev", complete.email());
        assertEquals("+41791234567", complete.phone());
    }

    @Test
    void rejectsInvalidContactFields()
    {
        assertThrows(NDefValueException.class, () -> new NDefContactCreator().firstName("Rithush").email("invalid").build());
        assertThrows(NDefValueException.class, () -> new NDefContactCreator().firstName("Rithush").email("a@b\r\nFN:Injected").build());
        assertThrows(NDefValueException.class, () -> new NDefContactCreator().firstName("Rithush").phone("+---").build());
        assertThrows(NDefValueException.class, () -> new NDefContactCreator().firstName("Rithush").website("mailto:a@b.ch").build());
    }

    @Test
    void buildsWpa2AndOpenWifi()
    {
        final NDefWifi secured = new NDefWifiCreator().ssid("WLAN").password("password").build();
        final NDefWifi open = new NDefWifiCreator().ssid("Guest").authentication(WifiAuthentication.OPEN).encryption(WifiEncryption.NONE).build();

        assertEquals(WifiAuthentication.WPA2_PERSONAL, secured.authentication());
        assertEquals(WifiEncryption.AES, secured.encryption());
        assertEquals("", open.password());
        assertFalse(secured.toString().contains("password=password"));
    }

    @Test
    void validatesSsidByteLengthWithoutTrimming()
    {
        assertDoesNotThrow(() -> new NDefWifiCreator().ssid("ä".repeat(16)).password("password").build());
        assertThrows(NDefValueException.class, () -> new NDefWifiCreator().ssid("ä".repeat(17)).password("password").build());
        assertEquals(" WLAN ", new NDefWifiCreator().ssid(" WLAN ").password("password").build().ssid());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "ä2345678", "abc\n123456"})
    void rejectsInvalidWifiPasswords(final String password)
    {
        assertThrows(NDefValueException.class, () -> new NDefWifiCreator().ssid("WLAN").password(password).build());
    }

    @Test
    void acceptsA64DigitHexadecimalKey()
    {
        assertDoesNotThrow(() -> new NDefWifiCreator().ssid("WLAN").password("a".repeat(64)).build());
        assertThrows(NDefValueException.class, () -> new NDefWifiCreator().ssid("WLAN").password("g".repeat(64)).build());
    }

    @Test
    void rejectsContradictoryWifiSettings()
    {
        assertThrows(NDefValueException.class, () -> new NDefWifiCreator().ssid("Guest").authentication(WifiAuthentication.OPEN).build());
        assertThrows(NDefValueException.class, () -> new NDefWifiCreator().ssid("Guest").authentication(WifiAuthentication.OPEN).encryption(WifiEncryption.NONE).password("password").build());
        assertThrows(NDefValueException.class, () -> new NDefWifiCreator().ssid("WLAN").encryption(WifiEncryption.NONE).password("password").build());
        assertThrows(NDefValueException.class, () -> new NDefWifiCreator().ssid("WLAN").authentication(null).password("password").build());
    }

    @Test
    void messageCreatorRejectsMissingContentsAndKeepsBuiltMessagesStable()
    {
        final NDefMessageCreator creator = new NDefMessageCreator();
        assertThrows(NDefEncodingException.class, creator::build);
        assertThrows(NDefValueException.class, () -> creator.add(null));

        creator.add(new NDefUriCreator().uri("https://a.ch").build());
        final NDefMessage first = creator.build();
        final byte[] expected = first.data();
        creator.add(new NDefTextCreator().text("Hallo").build());
        final NDefMessage second = creator.build();

        assertArrayEquals(expected, first.data());
        assertTrue(second.data().length > first.data().length);
    }
}
