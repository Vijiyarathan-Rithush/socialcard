package com.rvi.presentation;

import com.rvi.service.IBatchService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainController
{
    private final IBatchService service;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    @FXML private ComboBox<String> readers;
    @FXML private TextField destination;
    @FXML private Button refresh;
    @FXML private Button start;
    @FXML private Button stop;
    @FXML private Label status;
    @FXML private Label count;
    @FXML private ListView<String> activity;
    private boolean closed;

    public MainController(final IBatchService service)
    {
        this.service = Objects.requireNonNull(service);
    }

    @FXML
    private void initialize()
    {
        refreshReaders();
    }

    @FXML
    private void refreshReaders()
    {
        refresh.setDisable(true);
        executor.execute(() -> {
            try
            {
                final var names = service.readers();
                Platform.runLater(() -> {
                    if (closed) return;
                    readers.getItems().setAll(names);
                    readers.getSelectionModel().selectFirst();
                    refresh.setDisable(false);
                    show(names.isEmpty() ? "Connect a reader and refresh." : "Ready to start a batch.");
                });
            }
            catch (Exception exception)
            {
                Platform.runLater(() -> {
                    refresh.setDisable(false);
                    show(exception.getMessage());
                });
            }
        });
    }

    @FXML
    private void startBatch()
    {
        final String reader = readers.getValue();
        final String url = destination.getText().strip();
        if (reader == null)
        {
            show("Select a reader first.");
            return;
        }
        setActive(true);
        executor.execute(() -> {
            try
            {
                service.start(reader, url, update -> Platform.runLater(() -> {
                    if (closed) return;
                    show(update.message());
                    count.setText(Integer.toString(update.successfulCards()));
                    if (update.stopped()) setActive(false);
                }));
            }
            catch (RuntimeException exception)
            {
                Platform.runLater(() -> {
                    show(exception.getMessage());
                    setActive(false);
                });
            }
        });
    }

    @FXML
    private void stopBatch()
    {
        stop.setDisable(true);
        executor.execute(service::stop);
    }

    private void setActive(final boolean active)
    {
        readers.setDisable(active);
        destination.setDisable(active);
        refresh.setDisable(active);
        start.setDisable(active);
        stop.setDisable(!active);
    }

    private void show(final String message)
    {
        status.setText(message);
        activity.getItems().addFirst(message);
        if (activity.getItems().size() > 100)
        {
            activity.getItems().removeLast();
        }
    }

    public void close(final Runnable onClosed)
    {
        closed = true;
        setActive(true);
        stop.setDisable(true);
        show("Finishing the current write ...");
        executor.execute(() -> {
            try
            {
                service.close();
            }
            finally
            {
                Platform.runLater(onClosed);
            }
        });
        executor.shutdown();
    }
}
