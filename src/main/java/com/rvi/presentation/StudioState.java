package com.rvi.presentation;

import com.rvi.domain.ICardStatus;
import com.rvi.domain.ILinkValidation;

/** Pure presentation rules, independent of JavaFX controls and hardware. */
record StudioState(ICardStatus card, ILinkValidation validation)
{
    boolean readable()
    {
        return card instanceof ICardStatus.Ready;
    }

    boolean writable()
    {
        return card instanceof ICardStatus.Ready(var writable, var capacity)
                && writable
                && validation instanceof ILinkValidation.Valid(var bytes, var ignored)
                && bytes <= capacity;
    }

    int usedBytes()
    {
        return switch (validation)
        {
            case ILinkValidation.Valid(var bytes, var ignored) -> bytes;
            case ILinkValidation.Invalid ignored -> 0;
        };
    }

    String cardName()
    {
        return switch (card)
        {
            case ICardStatus.NoCard ignored -> "No card";
            case ICardStatus.Unavailable ignored -> "Unavailable";
            case ICardStatus.Unsupported ignored -> "Unsupported card";
            case ICardStatus.Ready ignored -> "NTAG215 compatible";
        };
    }

    String cardDescription()
    {
        return switch (card)
        {
            case ICardStatus.NoCard ignored -> "Place a card on the reader";
            case ICardStatus.Unavailable ignored -> "Check the reader connection";
            case ICardStatus.Unsupported ignored -> "Card format is not supported";
            case ICardStatus.Ready(var writable, var ignored) -> writable
                    ? "Card detected" : "Card detected · writing is unavailable";
        };
    }

    String validationText()
    {
        return switch (validation)
        {
            case ILinkValidation.Invalid(var message) -> message;
            case ILinkValidation.Valid(var bytes, var ignored)
                    when card instanceof ICardStatus.Ready(var writable, var capacity)
                    && bytes > capacity -> "The links exceed the card's available storage.";
            case ILinkValidation.Valid ignored -> "Valid HTTPS links";
        };
    }

    String capacityText()
    {
        return switch (card)
        {
            case ICardStatus.Ready(var ignored, var capacity) -> usedBytes() + " / " + capacity + " bytes";
            default -> usedBytes() + " bytes · no supported card";
        };
    }

    double memoryProgress()
    {
        return card instanceof ICardStatus.Ready(var ignored, var capacity)
                ? Math.min(1.0, (double) usedBytes() / capacity) : 0;
    }

    boolean validationError()
    {
        return validation instanceof ILinkValidation.Invalid
                || card instanceof ICardStatus.Ready(var ignored, var capacity) && usedBytes() > capacity;
    }
}
