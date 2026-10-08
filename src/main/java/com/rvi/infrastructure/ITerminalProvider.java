package com.rvi.infrastructure;

import java.util.List;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;

/** Injectable terminal discovery for PC/SC and hardware-free tests. */
@FunctionalInterface
public interface ITerminalProvider
{
    List<CardTerminal> terminals() throws CardException;
}
