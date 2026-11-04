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

    public NDefWifiCreator ssid(final String ssid)
    {
        this.ssid = ssid;
        return this;
    }

    public NDefWifiCreator authentication(final WifiAuthentication authentication)
    {
        this.authentication = authentication;
        return this;
    }

    public NDefWifiCreator encryption(final WifiEncryption encryption)
    {
        this.encryption = encryption;
        return this;
    }

    public NDefWifiCreator password(final String password)
    {
        this.password = password;
        return this;
    }

    public NDefWifi build()
    {
        return new NDefWifi(ssid, authentication, encryption, password);
    }
}
