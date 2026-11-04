package com.rvi.application.creator;

import com.rvi.domain.NDefWifi;
import com.rvi.domain.WifiAuthentication;
import com.rvi.domain.WifiEncryption;

public final class NDefWifiCreator
{
    private String ssid;
    private WifiAuthentication authentication = WifiAuthentication.WPA2_PERSONAL;
    private WifiEncryption encryption = WifiEncryption.AES;
    private String password;

    /** Sets an SSID of 1-32 UTF-8 bytes. */
    public NDefWifiCreator ssid(final String ssid)
    {
        this.ssid = ssid;
        return this;
    }

    /** Sets authentication; defaults to WPA2_PERSONAL. Open networks also require NONE encryption. */
    public NDefWifiCreator authentication(final WifiAuthentication authentication)
    {
        this.authentication = authentication;
        return this;
    }

    /** Sets encryption; defaults to AES. Use NONE for an open network. */
    public NDefWifiCreator encryption(final WifiEncryption encryption)
    {
        this.encryption = encryption;
        return this;
    }

    /** Sets an ASCII WPA2 passphrase (8-63 characters) or 64 hex digits. Open networks require an empty password. */
    public NDefWifiCreator password(final String password)
    {
        this.password = password;
        return this;
    }

    /** Validates collected values and returns a new immutable value. Does not access hardware. */
    public NDefWifi build()
    {
        return new NDefWifi(ssid, authentication, encryption, password);
    }
}
