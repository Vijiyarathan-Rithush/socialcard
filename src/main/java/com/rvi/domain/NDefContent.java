package com.rvi.domain;

public sealed interface NDefContent permits NDefUri, NDefText, NDefContact, NDefWifi
{
}
