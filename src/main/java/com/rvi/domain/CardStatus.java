package com.rvi.domain;

public record CardStatus(boolean present, boolean supported, boolean writable, String name, int capacity)
{
}
