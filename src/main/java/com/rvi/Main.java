package com.rvi;

import com.rvi.presentation.NfcStudioApplication;
import com.rvi.diagnostics.NativeBuildVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Main
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(final String[] args)
    {
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) ->
                LOGGER.error("Uncaught exception in thread {}", thread.getName(), failure));
        if (args.length > 0 && "--verify-native".equals(args[0]))
        {
            System.exit(NativeBuildVerifier.run(args));
            return;
        }
        try
        {
            NfcStudioApplication.launch(NfcStudioApplication.class, args);
        }
        catch (RuntimeException | LinkageError failure)
        {
            LOGGER.error("Application startup failed", failure);
            System.exit(1);
        }
    }
}
