package com.rvi.presentation;

import com.rvi.domain.ICardStatus;
import com.rvi.domain.ILinkValidation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class StudioStateTest
{
    @Test
    void enablesWritingOnlyForValidDataOnAWritableCardWithEnoughSpace()
    {
        assertTrue(state(true, 496, 496).writable());
        assertFalse(state(true, 496, 500).writable());
        assertFalse(state(false, 496, 32).writable());
        assertFalse(new StudioState(new ICardStatus.Ready(true, 496), new ILinkValidation.Invalid("Invalid URL")).writable());
        assertFalse(new StudioState(new ICardStatus.NoCard(), new ILinkValidation.Valid(32, 29)).writable());
    }

    @Test
    void readOnlyCardsRemainReadable()
    {
        assertTrue(state(false, 496, 32).readable());
        assertFalse(new StudioState(new ICardStatus.Unavailable(), new ILinkValidation.Valid(32, 29)).readable());
        assertFalse(new StudioState(new ICardStatus.Unsupported(), new ILinkValidation.Valid(32, 29)).readable());
    }

    @Test
    void reportsOversizedInputInsteadOfSayingItIsReadyToWrite()
    {
        final var state = state(true, 496, 500);
        assertTrue(state.validationError());
        assertTrue(state.validationText().contains("exceed"));
        assertEquals(1.0, state.memoryProgress());
    }

    @Test
    void cardStatesRejectImpossibleCapacity()
    {
        assertThrows(IllegalArgumentException.class, () -> new ICardStatus.Ready(true, 0));
    }

    private static StudioState state(boolean writable, int capacity, int used)
    {
        return new StudioState(new ICardStatus.Ready(writable, capacity), new ILinkValidation.Valid(used, used - 3));
    }
}
