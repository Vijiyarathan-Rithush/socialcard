package com.rvi.application;

public record ReaderStatus(State state, String message, int successfulCards)
{
    public enum State
    {
        READY, ACTIVE, WRITING, SUCCESS, STOPPING, STOPPED, ERROR
    }
}
