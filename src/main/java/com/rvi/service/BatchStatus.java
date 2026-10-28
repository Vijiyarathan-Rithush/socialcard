package com.rvi.service;

public record BatchStatus(State state, String message, int successfulCards)
{
    public enum State
    {
        READY, ACTIVE, WRITING, SUCCESS, STOPPING, STOPPED, ERROR
    }

    public boolean stopped()
    {
        return state == State.STOPPED;
    }
}
