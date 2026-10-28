package com.rvi.presentation;

import com.rvi.service.BatchStatus;
import com.rvi.service.IBatchService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MainControllerTest
{
    @Test
    void loadsViewAndClosesService() throws Exception
    {
        final var failure = new AtomicReference<Throwable>();
        final var done = new CountDownLatch(1);
        Platform.startup(() -> {
            try
            {
                final var service = new StubService();
                final var controller = new MainController(service);
                final var loader = new FXMLLoader(getClass().getResource("/com/rvi/main.fxml"));
                loader.setControllerFactory(type -> controller);
                final Parent view = loader.load();
                assertEquals("0", ((Label) view.lookup("#count")).getText());
                controller.close(() -> {
                    if (!service.closed) failure.set(new AssertionError("Service was not closed"));
                    done.countDown();
                });
            }
            catch (Throwable exception)
            {
                failure.set(exception);
                done.countDown();
            }
        });
        assertTrue(done.await(15, TimeUnit.SECONDS));
        Platform.exit();
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static final class StubService implements IBatchService
    {
        private volatile boolean closed;
        public List<String> readers() { return List.of("Test reader"); }
        public void start(String reader, String url, Consumer<BatchStatus> callback) {}
        public void stop() {}
        public void close() { closed = true; }
    }
}
