package com.rvi.presentation;

import com.rvi.domain.ICardStatus;
import com.rvi.domain.ILinkValidation;
import com.rvi.service.IReaderService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;

/** Adapts user actions and service results to the view. */
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

    private final IReaderService service;
    private final ReaderTaskExecutor tasks = new ReaderTaskExecutor();
    private LinkEditor links;
    private CardStatusMonitor monitor;
    private StudioState state = new StudioState(new ICardStatus.NoCard(),
            new ILinkValidation.Invalid("Add at least one HTTPS link."));

    public StudioController(final IReaderService service)
    {
        this.service = Objects.requireNonNull(service);
    }

    @FXML
    private void initialize()
    {
        readerChoice.disableProperty().bind(tasks.busyProperty());
        refreshButton.disableProperty().bind(tasks.busyProperty());
        linksBox.disableProperty().bind(tasks.busyProperty());
        addButton.disableProperty().bind(tasks.busyProperty());
        busyLabel.visibleProperty().bind(tasks.busyProperty());
        busyLabel.managedProperty().bind(tasks.busyProperty());
        links = new LinkEditor(linksBox, this::validateLinks);
        monitor = new CardStatusMonitor(service, tasks, readerChoice::getValue, this::showStatus);
        tasks.busyProperty().addListener((observable, previous, current) ->
        {
            monitor.invalidate();
            render();
            if (!current)
            {
                monitor.inspect();
            }
        });
        readerChoice.valueProperty().addListener((observable, previous, current) ->
        {
            monitor.invalidate();
            showStatus(new ICardStatus.NoCard());
            monitor.inspect();
        });
        links.replace(List.of("https://github.com/your-name", "https://instagram.com/your-name",
                "https://linkedin.com/in/your-name"));
        refreshReaders();
        monitor.start();
    }

    @FXML
    private void refreshReaders()
    {
        final String previous = readerChoice.getValue();
        tasks.run("list readers", service::readers, readers ->
        {
            readerChoice.getItems().setAll(readers);
            readerChoice.setValue(previous != null && readers.contains(previous) ? previous
                    : readers.isEmpty() ? null : readers.getFirst());
            showStatus(new ICardStatus.NoCard());
            statusLabel.setText("Ready");
        }, this::showFailure);
    }

    @FXML
    private void addLink()
    {
        if (!tasks.busy() && !tasks.stopping())
        {
            links.add();
        }
    }

    private void validateLinks()
    {
        final List<String> urls = links.urls();
        state = new StudioState(state.card(), service.validateLinks(urls));
        countLabel.setText(urls.size() + (urls.size() == 1 ? " entry" : " entries"));
        footerCount.setText(urls.size() + (urls.size() == 1 ? " link" : " links"));
        render();
    }

    private void showStatus(final ICardStatus status)
    {
        state = new StudioState(status, state.validation());
        render();
    }

    private void render()
    {
        final boolean blocked = tasks.busy() || tasks.stopping() || readerChoice.getValue() == null;
        readButton.setDisable(blocked || !state.readable());
        writeButton.setDisable(blocked || !state.writable());
        cardName.setText(state.cardName());
        cardState.setText(state.cardDescription());
        capacityLabel.setText(state.capacityText());
        memoryBar.setProgress(state.memoryProgress());
        validationLabel.setText(state.validationText());
        validationLabel.getStyleClass().remove("error");
        if (state.validationError())
        {
            validationLabel.getStyleClass().add("error");
        }
        readerStatus.setText(readerChoice.getValue() == null ? "No reader"
                : state.card() instanceof ICardStatus.Unavailable ? "Check connection" : "Reader available");
    }

    @FXML
    private void readCard()
    {
        if (readButton.isDisabled() || tasks.stopping())
        {
            return;
        }
        final String reader = readerChoice.getValue();
        statusLabel.setText("Reading card …");
        tasks.run("read card", () -> service.read(reader), urls ->
        {
            links.replace(urls);
            statusLabel.setText(urls.isEmpty() ? "The card contains no links" : "Links loaded from the card");
        }, this::showFailure);
    }

    @FXML
    private void writeCard()
    {
        if (writeButton.isDisabled() || tasks.stopping())
        {
            return;
        }
        final Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Write card");
        confirmation.setHeaderText("Replace the existing NDEF content?");
        confirmation.setContentText("Write " + links.urls().size()
                + " links and verify the result. Keep the card on the reader until writing finishes.");
        confirmation.initOwner(writeButton.getScene().getWindow());
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK
                || writeButton.isDisabled() || tasks.stopping())
        {
            return;
        }
        final String reader = readerChoice.getValue();
        final List<String> urls = links.urls();
        statusLabel.setText("Writing and verifying card …");
        tasks.run("write card", () ->
        {
            service.write(reader, urls);
            return null;
        }, ignored -> statusLabel.setText("Links saved · verification successful"), this::showFailure);
    }

    private void showFailure(final Throwable failure)
    {
        if (tasks.stopping())
        {
            return;
        }
        statusLabel.setText("Operation failed");
        final Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("NFC Studio");
        alert.setHeaderText("Operation failed");
        final String message = failure.getMessage();
        alert.setContentText(message == null || message.isBlank()
                ? "The operation could not be completed. See the application log for details." : message);
        alert.initOwner(writeButton.getScene().getWindow());
        alert.showAndWait();
    }

    /** Returns false while accepted hardware operations are still finishing. */
    public boolean requestClose(final Runnable closeWindow)
    {
        monitor.close();
        if (tasks.requestClose(closeWindow))
        {
            return true;
        }
        statusLabel.setText("Finishing the current operation before closing …");
        writeButton.getScene().getRoot().setDisable(true);
        return false;
    }

    @Override
    public void close()
    {
        if (monitor != null)
        {
            monitor.close();
        }
        tasks.close();
    }
}
