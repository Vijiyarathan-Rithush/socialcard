package com.rvi.presentation;

import com.rvi.domain.ICardStatus;
import com.rvi.domain.ILinkValidation;
import com.rvi.service.IReaderService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

final class PresentationTest
{
    @BeforeAll
    static void startToolkit() throws Exception
    {
        final var started = new CompletableFuture<Void>();
        Platform.startup(() ->
        {
            Platform.setImplicitExit(false);
            started.complete(null);
        });
        started.get(10, TimeUnit.SECONDS);
    }

    @AfterAll
    static void stopToolkit()
    {
        Platform.exit();
    }

    @Test
    void loadsFxmlWithAnInjectedServiceAndNoHardware() throws Exception
    {
        onFx(() ->
        {
            final var controller = new StudioController(new FakeService());
            try
            {
                final var loader = new FXMLLoader(getClass().getResource("/presentation/studio.fxml"));
                loader.setControllerFactory(type -> controller);
                final Parent view = loader.load();
                assertNotNull(view);
                assertSame(controller, loader.getController());
                assertTrue(((Button) loader.getNamespace().get("writeButton")).isDisabled());
                assertEquals("Write to card", ((Button) loader.getNamespace().get("writeButton")).getText());
            }
            finally
            {
                controller.close();
            }
            return null;
        });
    }

    @Test
    void initialRefreshSelectsTheFirstReaderFromAnImmutableList() throws Exception
    {
        final var selected = new CompletableFuture<String>();
        final var controller = onFx(() ->
        {
            final var value = new StudioController(new FakeService()
            {
                @Override public List<String> readers() { return List.of("test reader"); }
            });
            final var loader = new FXMLLoader(getClass().getResource("/presentation/studio.fxml"));
            loader.setControllerFactory(type -> value);
            loader.load();
            @SuppressWarnings("unchecked")
            final var choice = (ComboBox<String>) loader.getNamespace().get("readerChoice");
            choice.valueProperty().addListener((observable, previous, current) -> selected.complete(current));
            return value;
        });
        try
        {
            assertEquals("test reader", selected.get(10, TimeUnit.SECONDS));
        }
        finally
        {
            onFx(() -> { controller.close(); return null; });
        }
    }

    @Test
    void windowCloseWaitsForAcceptedWriteAndDoesNotInterruptIt() throws Exception
    {
        final var started = new CountDownLatch(1);
        final var finishWrite = new CountDownLatch(1);
        final var closed = new CompletableFuture<Void>();
        final var completed = new AtomicBoolean();
        final var interrupted = new AtomicBoolean();
        final ReaderTaskExecutor tasks = onFx(ReaderTaskExecutor::new);
        try
        {
            onFx(() ->
            {
                tasks.run("test write", () ->
                {
                    started.countDown();
                    try
                    {
                        if (!finishWrite.await(10, TimeUnit.SECONDS))
                        {
                            throw new IllegalStateException("Test write timed out.");
                        }
                    }
                    catch (InterruptedException failure)
                    {
                        interrupted.set(true);
                        throw failure;
                    }
                    completed.set(true);
                    return null;
                }, ignored -> {}, closed::completeExceptionally);
                return null;
            });
            assertTrue(started.await(10, TimeUnit.SECONDS));
            assertFalse(onFx(() -> tasks.requestClose(() ->
            {
                tasks.close();
                closed.complete(null);
            })));
            assertFalse(closed.isDone());
            assertFalse(completed.get());
            finishWrite.countDown();
            closed.get(10, TimeUnit.SECONDS);
            assertTrue(completed.get());
            assertFalse(interrupted.get());
        }
        finally
        {
            finishWrite.countDown();
            onFx(() -> { tasks.close(); return null; });
        }
    }

    @Test
    void ignoresInspectionResultsForThePreviouslySelectedReader() throws Exception
    {
        final var started = new CountDownLatch(1);
        final var finishInspection = new CountDownLatch(1);
        final var inspected = new CompletableFuture<Void>();
        final var selected = new AtomicReference<>("first");
        final var reported = new AtomicReference<ICardStatus>();
        final ReaderTaskExecutor tasks = onFx(ReaderTaskExecutor::new);
        final var service = new FakeService()
        {
            @Override public ICardStatus status(String reader)
            {
                started.countDown();
                try
                {
                    if (!finishInspection.await(10, TimeUnit.SECONDS))
                    {
                        throw new IllegalStateException("Inspection timed out.");
                    }
                }
                catch (InterruptedException failure)
                {
                    throw new IllegalStateException(failure);
                }
                return new ICardStatus.Ready(true, 496);
            }
        };
        final CardStatusMonitor monitor = onFx(() -> new CardStatusMonitor(service, tasks, selected::get, reported::set));
        try
        {
            onFx(() -> { monitor.inspect(); return null; });
            assertTrue(started.await(10, TimeUnit.SECONDS));
            onFx(() ->
            {
                selected.set("second");
                monitor.invalidate();
                tasks.run("barrier", () -> null, ignored -> inspected.complete(null), inspected::completeExceptionally);
                return null;
            });
            finishInspection.countDown();
            inspected.get(10, TimeUnit.SECONDS);
            assertNull(reported.get());
        }
        finally
        {
            finishInspection.countDown();
            onFx(() -> { monitor.close(); tasks.close(); return null; });
        }
    }

    private static <T> T onFx(final Callable<T> action) throws Exception
    {
        final var result = new CompletableFuture<T>();
        Platform.runLater(() ->
        {
            try { result.complete(action.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(15, TimeUnit.SECONDS);
    }

    private static class FakeService implements IReaderService
    {
        @Override public List<String> readers() { return List.of(); }
        @Override public ICardStatus status(String reader) { return new ICardStatus.NoCard(); }
        @Override public ILinkValidation validateLinks(List<String> urls) { return new ILinkValidation.Valid(32, 29); }
        @Override public List<String> read(String reader) { return List.of(); }
        @Override public void write(String reader, List<String> urls) { fail("Unexpected write."); }
    }
}
