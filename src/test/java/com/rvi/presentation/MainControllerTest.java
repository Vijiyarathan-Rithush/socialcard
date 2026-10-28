package com.rvi.presentation;

import com.rvi.service.BatchStatus;
import com.rvi.service.IBatchService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MainControllerTest
{
    private MainController controller;
    private Parent view;
    private StubService service;

    @BeforeAll
    static void startJavaFx() throws Exception
    {
        final var ready = new CompletableFuture<Void>();
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            ready.complete(null);
        });
        ready.get(10, TimeUnit.SECONDS);
    }

    @AfterAll
    static void stopJavaFx()
    {
        Platform.exit();
    }

    @AfterEach
    void closeService() throws Exception
    {
        if (controller == null) return;
        final var closed = new CompletableFuture<Void>();
        onFx(() -> controller.close(() -> closed.complete(null)));
        closed.get(5, TimeUnit.SECONDS);
        assertTrue(service.closed);
    }

    @Test
    void switchesBetweenBatchStatesAndIgnoresPreviousBatchEvents() throws Exception
    {
        load(false);
        snapshot("ready");
        start();
        emit(BatchStatus.State.ACTIVE, 0);
        onFx(() -> {
            assertFalse(view.lookup("#setup").isVisible());
            assertFalse(view.lookup("#setup").isManaged());
            assertTrue(view.lookup("#session").isVisible());
            assertEquals("Place a card on the reader", label("status").getText());
        });
        snapshot("waiting");
        emit(BatchStatus.State.WRITING, 0);
        onFx(() -> assertEquals("Writing and verifying", label("status").getText()));
        emit(BatchStatus.State.SUCCESS, 3);
        onFx(() -> {
            assertEquals("Card verified", label("status").getText());
            assertEquals("3", label("count").getText());
        });
        snapshot("verified");
        emit(BatchStatus.State.ERROR, 3);
        onFx(() -> assertEquals("Could not write this card", label("status").getText()));
        snapshot("error");
        onFx(() -> button("stop").fire());
        await(() -> service.stopped);
        emit(BatchStatus.State.STOPPING, 3);
        onFx(() -> {
            assertTrue(view.lookup("#session").isVisible());
            assertTrue(button("stop").isDisabled());
        });
        emit(BatchStatus.State.STOPPED, 3);
        onFx(() -> {
            assertTrue(view.lookup("#setup").isVisible());
            assertFalse(view.lookup("#session").isManaged());
        });
        final var oldCallback = service.callback;
        service.callback = null;
        start();
        oldCallback.accept(new BatchStatus(BatchStatus.State.STOPPED, "Old batch", 99));
        onFx(() -> {
            assertTrue(view.lookup("#session").isVisible());
            assertEquals("0", label("count").getText());
        });
    }

    @Test
    void showsActionableReaderFailureAndDisablesStart() throws Exception
    {
        load(true);
        onFx(() -> {
            assertTrue(button("start").isDisabled());
            assertEquals("Reader unavailable", label("connection").getText());
            assertTrue(label("feedback").getText().contains("PC/SC"));
            assertFalse(label("feedback").getText().contains("list()"));
        });
    }

    @Test
    void returnsToSetupWhenServiceRejectsUrl() throws Exception
    {
        load(false);
        service.rejectStart = true;
        onFx(() -> {
            ((TextField) view.lookup("#destination")).setText("http://invalid.example");
            button("start").fire();
        });
        await(() -> service.startAttempted);
        awaitFx(() -> view.lookup("#setup").isVisible());
        onFx(() -> assertEquals("Use an HTTPS URL", label("feedback").getText()));
    }

    @Test
    void expandsActivityAndPreservesDestinationAfterStop() throws Exception
    {
        load(false);
        onFx(() -> {
            assertFalse(view.lookup("#activityPane").isManaged());
            button("activityToggle").fire();
            assertTrue(view.lookup("#activityPane").isManaged());
            button("activityToggle").fire();
            assertFalse(view.lookup("#activityPane").isVisible());
        });
        start();
        emit(BatchStatus.State.STOPPED, 0);
        onFx(() -> assertEquals("https://example.com", ((TextField) view.lookup("#destination")).getText()));
    }

    private void load(final boolean readerFailure) throws Exception
    {
        service = new StubService();
        service.readerFailure = readerFailure;
        onFx(() -> {
            controller = new MainController(service);
            final var loader = new FXMLLoader(getClass().getResource("/com/rvi/main.fxml"));
            loader.setControllerFactory(type -> controller);
            view = loader.load();
            new Scene(view, 560, 440);
        });
        awaitFx(() -> !button("refresh").isDisabled());
    }

    private void start() throws Exception
    {
        onFx(() -> {
            ((TextField) view.lookup("#destination")).setText("https://example.com");
            button("start").fire();
        });
        await(() -> service.callback != null);
    }

    private void emit(final BatchStatus.State state, final int count) throws Exception
    {
        service.callback.accept(new BatchStatus(state, "Test event: " + state, count));
        onFx(() -> {});
    }

    private Label label(final String id)
    {
        return (Label) view.lookup("#" + id);
    }

    private Button button(final String id)
    {
        return (Button) view.lookup("#" + id);
    }

    private void snapshot(final String name) throws Exception
    {
        final String directory = System.getProperty("socialcard.previewDirectory");
        if (directory == null) return;
        onFx(() -> {
            view.applyCss();
            view.layout();
            final var image = view.snapshot(null, null);
            final var buffer = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < buffer.getHeight(); y++)
            {
                for (int x = 0; x < buffer.getWidth(); x++)
                {
                    buffer.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                }
            }
            final var path = Path.of(directory, name + ".png");
            Files.createDirectories(path.getParent());
            ImageIO.write(buffer, "png", path.toFile());
        });
    }

    private static void awaitFx(final BooleanSupplier condition) throws Exception
    {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline)
        {
            final boolean[] result = {false};
            onFx(() -> result[0] = condition.getAsBoolean());
            if (result[0]) return;
            Thread.sleep(20);
        }
        fail("UI did not reach the expected state");
    }

    private static void await(final BooleanSupplier condition) throws Exception
    {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean())
        {
            if (System.nanoTime() > deadline) fail("Service did not reach the expected state");
            Thread.sleep(20);
        }
    }

    private static void onFx(final FxAction action) throws Exception
    {
        final var done = new CompletableFuture<Void>();
        Platform.runLater(() -> {
            try
            {
                action.run();
                done.complete(null);
            }
            catch (Throwable exception)
            {
                done.completeExceptionally(exception);
            }
        });
        done.get(5, TimeUnit.SECONDS);
    }

    @FunctionalInterface
    private interface FxAction
    {
        void run() throws Exception;
    }

    private static final class StubService implements IBatchService
    {
        private boolean readerFailure;
        private boolean rejectStart;
        private volatile boolean startAttempted;
        private volatile boolean stopped;
        private volatile boolean closed;
        private volatile Consumer<BatchStatus> callback;

        public List<String> readers()
        {
            if (readerFailure) throw new IllegalStateException("list() failed");
            return List.of("ACS ACR122U");
        }

        public void start(String reader, String url, Consumer<BatchStatus> callback)
        {
            startAttempted = true;
            if (rejectStart) throw new IllegalArgumentException("Use an HTTPS URL");
            this.callback = callback;
        }

        public void stop() { stopped = true; }
        public void close() { closed = true; }
    }
}
