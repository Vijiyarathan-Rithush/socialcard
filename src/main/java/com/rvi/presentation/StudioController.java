package com.rvi.presentation;

import com.rvi.domain.CardStatus;
import com.rvi.service.ReaderService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class StudioController implements AutoCloseable
{
    @FXML private ComboBox<String> readerChoice;
    @FXML private Button refreshButton;
    @FXML private VBox linksBox;
    @FXML private Button addButton;
    @FXML private Button readButton;
    @FXML private Button writeButton;
    @FXML private Label readerStatus;
    @FXML private Label cardName;
    @FXML private Label cardState;
    @FXML private Label capacityLabel;
    @FXML private Label validationLabel;
    @FXML private Label countLabel;
    @FXML private Label footerCount;
    @FXML private Label statusLabel;
    @FXML private Label busyLabel;
    @FXML private ProgressBar memoryBar;

    private final ReaderService service = new ReaderService();
    private final List<LinkRow> rows = new ArrayList<>();
    private final BooleanProperty busy = new SimpleBooleanProperty();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable ->
    {
        final Thread thread = new Thread(runnable, "nfc-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final Timeline poller = new Timeline(new KeyFrame(Duration.seconds(2), event -> inspectCard()));
    private CardStatus cardStatus = new CardStatus(false, false, false, "Keine Karte", 0);
    private boolean valid;
    private boolean refreshing;
    private int usedBytes;

    @FXML
    private void initialize()
    {
        readerChoice.disableProperty().bind(busy);
        refreshButton.disableProperty().bind(busy);
        linksBox.disableProperty().bind(busy);
        addButton.disableProperty().bind(busy);
        busyLabel.visibleProperty().bind(busy);
        readerChoice.setOnAction(event ->
        {
            if (!refreshing)
            {
                applyStatus(new CardStatus(false, false, false, "Keine Karte", 0));
                inspectCard();
            }
        });
        addRow("https://github.com/dein-name");
        addRow("https://instagram.com/dein-name");
        addRow("https://linkedin.com/in/dein-name");
        poller.setCycleCount(Timeline.INDEFINITE);
        refreshReaders();
        poller.play();
    }

    @FXML
    private void refreshReaders()
    {
        final String previous = readerChoice.getValue();
        run(service::readers, readers ->
        {
            refreshing = true;
            readerChoice.getItems().setAll(readers);

            if (readers.contains(previous))
            {
                readerChoice.setValue(previous);
            }
            else if (!readers.isEmpty())
            {
                readerChoice.getSelectionModel().selectFirst();
            }

            refreshing = false;
            readerStatus.setText("Kein Reader");

            if (!readers.isEmpty())
            {
                readerStatus.setText("Reader verfügbar");
            }

            applyStatus(new CardStatus(false, false, false, "Keine Karte", 0));
            statusLabel.setText("Bereit");
        }, true);
    }

    @FXML
    private void addLink()
    {
        addRow("");
        rows.get(rows.size() - 1).focus();
    }

    private void addRow(final String url)
    {
        final LinkRow[] holder = new LinkRow[1];
        holder[0] = new LinkRow(url, this::validate, () ->
        {
            rows.remove(holder[0]);
            linksBox.getChildren().remove(holder[0].root());
            validate();
        });
        rows.add(holder[0]);
        linksBox.getChildren().add(holder[0].root());
        validate();
    }

    private List<String> urls()
    {
        return rows.stream().map(LinkRow::url).toList();
    }

    private void validate()
    {
        for (int index = 0; index < rows.size(); index++)
        {
            rows.get(index).number(index + 1);
        }

        countLabel.setText(rows.size() + " Einträge");
        footerCount.setText(rows.size() + " Links");
        valid = false;
        usedBytes = 0;

        try
        {
            usedBytes = service.encodeURIs(urls()).data().length;
            valid = true;
            validationLabel.setText("HTTPS-Links gültig • maximal 254 NDEF-Bytes");
            validationLabel.getStyleClass().remove("error");
        }
        catch (IllegalArgumentException exception)
        {
            validationLabel.setText(exception.getMessage());

            if (!validationLabel.getStyleClass().contains("error"))
            {
                validationLabel.getStyleClass().add("error");
            }
        }

        updateMemory();
        updateActions();
    }

    private void inspectCard()
    {
        if (busy.get() || readerChoice.getValue() == null)
        {
            return;
        }

        final String reader = readerChoice.getValue();
        run(() -> service.status(reader), this::applyStatus, false);
    }

    private void applyStatus(final CardStatus status)
    {
        cardStatus = status;
        cardName.setText(status.name());
        cardState.setText("Karte auflegen");

        if (status.present())
        {
            cardState.setText("Format nicht unterstützt");

            if (status.supported())
            {
                cardState.setText("Karte erkannt • schreibgeschützt");

                if (status.writable())
                {
                    cardState.setText("Karte erkannt");
                }
            }
        }

        updateMemory();
        updateActions();
    }

    private void updateMemory()
    {
        capacityLabel.setText(usedBytes + " Bytes • keine Karte");
        memoryBar.setProgress(0);

        if (cardStatus.supported())
        {
            capacityLabel.setText(usedBytes + " / " + cardStatus.capacity() + " Bytes");
            memoryBar.setProgress((double) usedBytes / cardStatus.capacity());
        }
    }

    private void updateActions()
    {
        readButton.setDisable(busy.get() || !cardStatus.supported());
        writeButton.setDisable(busy.get() || !cardStatus.supported() || !cardStatus.writable() || !valid);
    }

    @FXML
    private void readCard()
    {
        final String reader = readerChoice.getValue();
        statusLabel.setText("Karte wird gelesen …");
        run(() -> service.read(reader), links ->
        {
            rows.clear();
            linksBox.getChildren().clear();
            links.forEach(this::addRow);
            validate();
            statusLabel.setText("Links von der Karte geladen");
        }, true);
    }

    @FXML
    private void writeCard()
    {
        final Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Karte beschreiben");
        confirmation.setHeaderText("Vorhandenen NDEF-Inhalt ersetzen?");
        confirmation.setContentText("Die aufgelegte Karte wird mit " + rows.size() + " Links beschrieben und anschliessend überprüft.");
        confirmation.initOwner(writeButton.getScene().getWindow());

        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK)
        {
            return;
        }

        final String reader = readerChoice.getValue();
        final List<String> links = urls();
        statusLabel.setText("Karte wird geschrieben und überprüft …");
        run(() ->
        {
            service.write(reader, links);
            return true;
        }, result -> statusLabel.setText("Links gespeichert • Überprüfung erfolgreich"), true);
    }

    private <T> void run(final Callable<T> action, final Consumer<T> success, final boolean showError)
    {
        if (busy.get())
        {
            return;
        }

        busy.set(true);
        updateActions();
        final Task<T> task = new Task<>()
        {
            @Override
            protected T call() throws Exception
            {
                return action.call();
            }
        };
        task.setOnSucceeded(event ->
        {
            busy.set(false);
            success.accept(task.getValue());
            updateActions();
        });
        task.setOnFailed(event ->
        {
            busy.set(false);
            applyStatus(new CardStatus(false, false, false, "Nicht verfügbar", 0));
            readerStatus.setText("Verbindung prüfen");
            statusLabel.setText("Reader oder Karte nicht verfügbar");

            if (showError)
            {
                final Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("NFC Studio");
                alert.setHeaderText("Aktion fehlgeschlagen");
                alert.setContentText(task.getException().getMessage());
                alert.initOwner(writeButton.getScene().getWindow());
                alert.showAndWait();
            }
        });
        worker.submit(task);
    }

    @Override
    public void close()
    {
        poller.stop();
        worker.shutdownNow();
    }
}
