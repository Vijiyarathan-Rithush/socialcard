package com.rvi.presentation;

import com.rvi.service.BatchStatus;
import com.rvi.service.IBatchService;
import com.rvi.service.ProfileService;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainController
{
    private static final DateTimeFormatter EVENT_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final PseudoClass SUCCESS = PseudoClass.getPseudoClass("success");
    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");
    private final IBatchService service;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    @FXML private VBox root;
    @FXML private VBox setup;
    @FXML private VBox session;
    @FXML private VBox activityPane;
    @FXML private ComboBox<String> readers;
    @FXML private TextField destination;
    @FXML private Button refresh;
    @FXML private Button start;
    @FXML private Button stop;
    @FXML private Button activityToggle;
    @FXML private Label connection;
    @FXML private Label feedback;
    @FXML private Label selectedReader;
    @FXML private Label selectedUrl;
    @FXML private Label symbol;
    @FXML private Label status;
    @FXML private Label instruction;
    @FXML private Label count;
    @FXML private ListView<String> activity;
    @FXML private ScrollPane setupScroll;
    @FXML private ToggleButton profileMode;
    @FXML private ToggleGroup modeGroup;
    @FXML private HBox modeSwitch;
    @FXML private VBox profileFields;
    @FXML private TextField profileName;
    @FXML private TextArea profileBio;
    @FXML private VBox linkRows;
    @FXML private Label capacity;
    @FXML private Label destinationLabel;
    @FXML private Label viewerLabel;
    @FXML private VBox writerDock;
    @FXML private VBox directPanel;
    @FXML private VBox previewColumn;
    @FXML private Label previewName;
    @FXML private Label previewBio;
    @FXML private VBox previewLinks;
    @FXML private ProgressBar capacityMeter;
    @FXML private Button addLinkButton;
    private final ProfileService profiles = new ProfileService();
    private final List<ProfileRow> profileRows = new ArrayList<>();
    private String preparedUrl;
    private record ProfileRow(HBox container, TextField label, TextField url) {}
    private boolean active;
    private boolean closed;
    private long generation;

    public MainController(final IBatchService service)
    {
        this.service = Objects.requireNonNull(service);
    }

    @FXML
    private void initialize()
    {
        modeGroup.selectedToggleProperty().addListener((observable, previous, selected) -> {
            if (selected == null && previous != null) previous.setSelected(true);
        });
        writerDock.visibleProperty().bind(setup.visibleProperty());
        writerDock.managedProperty().bind(writerDock.visibleProperty());
        setupScroll.visibleProperty().bind(setup.visibleProperty());
        setupScroll.managedProperty().bind(setup.visibleProperty());
        setup.managedProperty().bind(setup.visibleProperty());
        session.managedProperty().bind(session.visibleProperty());
        activityPane.managedProperty().bind(activityPane.visibleProperty());
        feedback.managedProperty().bind(feedback.visibleProperty());
        destination.visibleProperty().bind(profileMode.selectedProperty().not());
        destination.managedProperty().bind(destination.visibleProperty());
        destinationLabel.visibleProperty().bind(destination.visibleProperty());
        destinationLabel.managedProperty().bind(destination.visibleProperty());
        viewerLabel.setText(ProfileService.VIEWER_URL.replace("https://", "").replaceAll("/$", ""));
        directPanel.visibleProperty().bind(profileMode.selectedProperty().not());
        directPanel.managedProperty().bind(directPanel.visibleProperty());
        previewColumn.visibleProperty().bind(profileMode.selectedProperty());
        previewColumn.managedProperty().bind(previewColumn.visibleProperty());
        capacityMeter.visibleProperty().bind(profileMode.selectedProperty());
        capacityMeter.managedProperty().bind(capacityMeter.visibleProperty());
        viewerLabel.visibleProperty().bind(profileMode.selectedProperty());
        viewerLabel.managedProperty().bind(viewerLabel.visibleProperty());
        profileFields.managedProperty().bind(profileFields.visibleProperty());
        profileFields.visibleProperty().bind(profileMode.selectedProperty());
        profileMode.selectedProperty().addListener((observable, old, value) -> updateProfile());
        destination.textProperty().addListener((observable, old, value) -> updateProfile());
        profileName.textProperty().addListener((observable, old, value) -> updateProfile());
        profileBio.textProperty().addListener((observable, old, value) -> updateProfile());
        addLink();
        updateProfile();
        start.disableProperty().bind(Bindings.createBooleanBinding(
                () -> readers.getValue() == null || refresh.isDisabled()
                        || (profileMode.isSelected() ? preparedUrl == null : destination.getText().isBlank()),
                readers.valueProperty(), destination.textProperty(), refresh.disableProperty(),
                profileMode.selectedProperty(), capacity.textProperty()));
        refreshReaders();
    }

    @FXML
    private void addLink()
    {
        if (profileRows.size() >= 10) return;
        final var label = new TextField();
        label.setPromptText("Label");
        label.setAccessibleText("Link label");
        label.setPrefWidth(100);
        label.setMinWidth(85);
        label.setMaxWidth(120);
        final var url = new TextField();
        url.setPromptText("https://...");
        url.setAccessibleText("Link HTTPS URL");
        url.setMinWidth(120);
        HBox.setHgrow(url, Priority.ALWAYS);
        final var remove = new Button("×");
        remove.setAccessibleText("Remove link");
        remove.getStyleClass().add("remove-link");
        final var container = new HBox(8, label, url, remove);
        container.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        final var row = new ProfileRow(container, label, url);
        profileRows.add(row);
        linkRows.getChildren().add(container);
        addLinkButton.setDisable(profileRows.size() >= 10);
        label.textProperty().addListener((observable, old, value) -> updateProfile());
        url.textProperty().addListener((observable, old, value) -> updateProfile());
        remove.setOnAction(event -> {
            profileRows.remove(row);
            addLinkButton.setDisable(false);
            linkRows.getChildren().remove(container);
            updateProfile();
        });
        updateProfile();
    }

    private void updateProfile()
    {
        preparedUrl = null;
        updatePreview();
        capacityMeter.setProgress(0);
        capacityMeter.pseudoClassStateChanged(ERROR, false);
        capacity.pseudoClassStateChanged(ERROR, false);
        if (!profileMode.isSelected())
        {
            capacity.setText("Direct HTTPS link");
            return;
        }
        try
        {
            final var links = profileRows.stream()
                    .map(row -> new ProfileService.Link(row.label().getText(), row.url().getText()))
                    .toList();
            final String url = profiles.createUrl(profileName.getText(),
                    profileBio.getText(), links);
            final int bytes = profiles.encodedBytes(url);
            preparedUrl = url;
            capacityMeter.setProgress((double) bytes / ProfileService.CAPACITY_BYTES);
            capacity.setText(bytes + " / " + ProfileService.CAPACITY_BYTES + " bytes");
        }
        catch (IllegalArgumentException exception)
        {
            final boolean empty = profileName.getText().isBlank() && profileBio.getText().isBlank()
                    && profileRows.stream().allMatch(row -> row.label().getText().isBlank() && row.url().getText().isBlank());
            capacity.setText(empty ? "Add profile details" : exception.getMessage());
            capacity.pseudoClassStateChanged(ERROR, !empty);
            capacityMeter.pseudoClassStateChanged(ERROR, !empty);
        }
    }

    private void updatePreview()
    {
        previewName.setText(profileName.getText().isBlank() ? "Your name" : profileName.getText().strip());
        previewBio.setText(profileBio.getText().strip());
        previewBio.setVisible(!previewBio.getText().isBlank());
        previewBio.setManaged(previewBio.isVisible());
        previewLinks.getChildren().clear();
        for (final ProfileRow row : profileRows)
        {
            final String text = row.label().getText().isBlank() ? "Your link" : row.label().getText().strip();
            final var link = new Label(text + "  ↗");
            link.getStyleClass().add("preview-link");
            link.setMaxWidth(Double.MAX_VALUE);
            link.setWrapText(true);
            previewLinks.getChildren().add(link);
        }
    }

    @FXML
    private void refreshReaders()
    {
        if (closed || active) return;
        refresh.setDisable(true);
        connection.setText("Looking for readers ...");
        final String previous = readers.getValue();
        executor.execute(() -> {
            try
            {
                final var names = service.readers();
                onUi(() -> {
                    readers.getItems().setAll(names);
                    readers.getSelectionModel().select(previous != null && names.contains(previous)
                            ? previous : names.stream().findFirst().orElse(null));
                    refresh.setDisable(false);
                    connection.setText(names.isEmpty() ? "No reader connected" : "Reader connected");
                    root.pseudoClassStateChanged(ERROR, false);
                    showFeedback(names.isEmpty() ? "Connect an NFC reader, then refresh." : "", false);
                    addEvent(names.isEmpty() ? "No reader found." : "Reader connected.");
                });
            }
            catch (Exception exception)
            {
                onUi(() -> {
                    readers.getItems().clear();
                    refresh.setDisable(false);
                    connection.setText("Reader unavailable");
                    showFeedback("Could not access NFC readers. Check the connection, drivers and PC/SC service, then refresh.", true);
                    addEvent("Reader discovery failed: " + errorMessage(exception));
                });
            }
        });
    }

    @FXML
    private void startBatch()
    {
        if (closed || active || start.isDisabled()) return;
        final String reader = readers.getValue();
        final String url = profileMode.isSelected() ? preparedUrl : destination.getText().strip();
        final long current = ++generation;
        selectedReader.setText(reader);
        selectedUrl.setText(profileMode.isSelected() ? profileName.getText().strip() : url);
        count.setText("0");
        showFeedback("", false);
        setActive(true);
        showStatus("◎", "Preparing cards", "Preparing the reader ...", false, false);
        executor.execute(() -> {
            try
            {
                service.start(reader, url, update -> onUi(() -> {
                    if (current == generation) updateStatus(update);
                }));
            }
            catch (RuntimeException exception)
            {
                onUi(() -> {
                    if (current != generation) return;
                    setActive(false);
                    showFeedback(errorMessage(exception), true);
                    addEvent("Could not prepare cards: " + errorMessage(exception));
                });
            }
        });
    }

    private void updateStatus(final BatchStatus update)
    {
        count.setText(Integer.toString(update.successfulCards()));
        addEvent(update.message());
        switch (update.state())
        {
            case ACTIVE, READY -> showStatus("◎", "Place a card on the reader",
                    "Your card content will be written and verified.", false, false);
            case WRITING -> showStatus("···", "Writing and verifying",
                    "Keep the card on the reader.", false, false);
            case SUCCESS -> showStatus("✓", "Card verified",
                    "Remove the card, then place the next one.", true, false);
            case ERROR -> showStatus("!", "Could not write this card",
                    update.message(), false, true);
            case STOPPING -> showStatus("···", "Finishing the current card",
                    "Writing will stop after this card.", false, false);
            case STOPPED -> {
                setActive(false);
                showFeedback("Writing stopped. " + update.successfulCards() + " cards verified.", false);
            }
        }
    }

    private void showStatus(final String mark, final String title, final String detail,
                            final boolean success, final boolean error)
    {
        symbol.setText(mark);
        status.setText(title);
        instruction.setText(detail);
        root.pseudoClassStateChanged(SUCCESS, success);
        root.pseudoClassStateChanged(ERROR, error);
    }

    @FXML
    private void stopBatch()
    {
        if (closed || !active || stop.isDisabled()) return;
        stop.setDisable(true);
        executor.execute(service::stop);
    }

    @FXML
    private void toggleActivity()
    {
        final boolean expanded = !activityPane.isVisible();
        activityPane.setVisible(expanded);
        activityToggle.setText(expanded ? "Activity ▴" : "Activity ▾");
        activityToggle.setAccessibleText(expanded ? "Collapse activity" : "Expand activity");
    }

    private void setActive(final boolean value)
    {
        active = value;
        modeSwitch.setDisable(value);
        setup.setVisible(!value);
        session.setVisible(value);
        stop.setDisable(!value);
        if (!value)
        {
            root.pseudoClassStateChanged(SUCCESS, false);
            root.pseudoClassStateChanged(ERROR, false);
        }
    }

    private void showFeedback(final String message, final boolean error)
    {
        feedback.setText(message);
        feedback.setVisible(!message.isBlank());
        feedback.pseudoClassStateChanged(ERROR, error);
    }

    private void addEvent(final String message)
    {
        activity.getItems().addFirst(LocalTime.now().format(EVENT_TIME) + "   " + message);
        if (activity.getItems().size() > 100) activity.getItems().removeLast();
    }

    private void onUi(final Runnable action)
    {
        Platform.runLater(() -> {
            if (!closed) action.run();
        });
    }

    private String errorMessage(final Exception exception)
    {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "The operation could not be completed. Please try again." : exception.getMessage();
    }

    public void close(final Runnable onClosed)
    {
        if (closed) return;
        closed = true;
        setActive(true);
        stop.setDisable(true);
        showStatus("···", "Closing SocialCard", "Finishing any active write ...", false, false);
        root.setDisable(true);
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

