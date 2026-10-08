package com.rvi.presentation;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Serializes hardware access. All public methods and callbacks run on the FX thread. */
final class ReaderTaskExecutor implements AutoCloseable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ReaderTaskExecutor.class);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("nfc-worker").factory());
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper();
    private boolean closing;
    private boolean closed;
    private int pending;
    private Runnable whenIdle;

    ReadOnlyBooleanProperty busyProperty()
    {
        return busy.getReadOnlyProperty();
    }

    boolean busy()
    {
        return busy.get();
    }

    boolean stopping()
    {
        return closing || closed;
    }

    <T> void run(final String description, final Callable<T> action,
                 final Consumer<T> success, final Consumer<Throwable> failure)
    {
        if (!busy() && !stopping())
        {
            submit(action, success, error ->
            {
                LOGGER.error("Reader operation '{}' failed", description, error);
                failure.accept(error);
            }, true);
        }
    }

    <T> boolean inspect(final Callable<T> action, final Consumer<T> success,
                        final Consumer<Throwable> failure)
    {
        if (busy() || stopping())
        {
            return false;
        }
        submit(action, success, failure, false);
        return true;
    }

    /** Prevents new work and closes the window only after every accepted task finishes. */
    boolean requestClose(final Runnable closeWindow)
    {
        closing = true;
        if (pending == 0)
        {
            return true;
        }
        whenIdle = closeWindow;
        return false;
    }

    private <T> void submit(final Callable<T> action, final Consumer<T> success,
                            final Consumer<Throwable> failure, final boolean foreground)
    {
        pending++;
        if (foreground)
        {
            busy.set(true);
        }
        final Task<T> task = new Task<>()
        {
            @Override
            protected T call() throws Exception
            {
                return action.call();
            }
        };
        task.setOnSucceeded(event -> complete(() -> success.accept(task.getValue()), foreground));
        task.setOnFailed(event -> complete(() -> failure.accept(task.getException()), foreground));
        task.setOnCancelled(event -> complete(() -> {}, foreground));
        worker.execute(task);
    }

    private void complete(final Runnable callback, final boolean foreground)
    {
        try
        {
            if (!closed)
            {
                callback.run();
            }
        }
        catch (RuntimeException exception)
        {
            LOGGER.error("Could not present the reader operation result", exception);
        }
        finally
        {
            pending--;
            if (foreground)
            {
                busy.set(false);
            }
            if (pending == 0 && whenIdle != null)
            {
                final Runnable callbackWhenIdle = whenIdle;
                whenIdle = null;
                callbackWhenIdle.run();
            }
        }
    }

    @Override
    public void close()
    {
        closed = true;
        closing = true;
        // Never interrupt a write after its TLV length has been cleared. The non-daemon
        // worker also finishes accepted work if shutdown bypasses the window handler.
        worker.shutdown();
    }
}
