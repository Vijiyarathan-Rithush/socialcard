package com.rvi.service;

public record BatchStatus(String message, int successfulCards, boolean stopped)
{
}
