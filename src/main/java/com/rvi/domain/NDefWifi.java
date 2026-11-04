package com.rvi.domain;

import com.rvi.domain.exception.NDefValueException;

import java.nio.charset.StandardCharsets;

public record NDefWifi(String ssid, WifiAuthentication authentication, WifiEncryption encryption, String password) implements NDefContent
{
    public NDefWifi
    {
        if (ssid == null || ssid.isEmpty() || ssid.getBytes(StandardCharsets.UTF_8).length > 32)
        {
            throw new NDefValueException("SSID must contain between 1 and 32 UTF-8 bytes");
        }

        if (authentication == null || encryption == null)
        {
            throw new NDefValueException("Wi-Fi authentication and encryption are required");
        }

        if (password == null)
        {
            password = "";
        }

        if (authentication == WifiAuthentication.OPEN)
        {
            if (encryption != WifiEncryption.NONE || !password.isEmpty())
            {
                throw new NDefValueException("Open Wi-Fi requires no encryption and no password");
            }
        }
        else
        {
            if (encryption != WifiEncryption.AES)
            {
                throw new NDefValueException("WPA2-Personal requires AES encryption");
            }

            if (!password.matches("[\\x20-\\x7E]{8,63}") && !password.matches("[0-9A-Fa-f]{64}"))
            {
                throw new NDefValueException("Expected an 8-63 character ASCII passphrase or a 64-digit hexadecimal key");
            }
        }
    }

    @Override
    public String toString()
    {
        return "NDefWifi[ssid=%s, authentication=%s, encryption=%s, password=<redacted>]".formatted(ssid, authentication, encryption);
    }
}
