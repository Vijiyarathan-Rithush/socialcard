package com.rvi.application;

import javax.smartcardio.CardTerminal;

public interface IReaderListener
{
    void onCardInserted(CardTerminal terminal);

    void onCardRemoved(CardTerminal terminal);

    void onError(Exception exception);
}
