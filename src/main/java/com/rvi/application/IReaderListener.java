package com.rvi.application;

import javax.smartcardio.CardTerminal;

/** Monitor callbacks execute on a background thread. Keep them short and dispatch UI work. */
public interface IReaderListener
{
    /** Called when a card is detected; the monitor itself does not connect to it. */
    void onCardInserted(CardTerminal terminal);

    /** Called after the previously detected card is removed. */
    void onCardRemoved(CardTerminal terminal);

    /** Reports a polling or callback failure while active; monitoring then stops. */
    void onError(Exception exception);
}