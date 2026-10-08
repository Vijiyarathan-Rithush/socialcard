package com.rvi.presentation;

import com.rvi.infrastructure.PcscReaderRepository;
import com.rvi.service.ReaderService;
import com.rvi.service.NdefUriConverter;
import com.rvi.service.IReaderService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;
import java.util.Objects;

public final class NfcStudioApplication extends Application
{
    private StudioController controller;

    @Override
    public void start(final Stage stage) throws Exception
    {
        final URL fxml = Objects.requireNonNull(getClass().getResource("/presentation/studio.fxml"), "Missing studio.fxml resource.");
        final URL css = Objects.requireNonNull(getClass().getResource("/presentation/studio.css"), "Missing studio.css resource.");

        final IReaderService service = new ReaderService(new PcscReaderRepository(), new NdefUriConverter());
        final FXMLLoader loader = new FXMLLoader(fxml);
        controller = new StudioController(service);
        loader.setControllerFactory(type ->
        {
            if (type != StudioController.class)
            {
                throw new IllegalArgumentException("Unexpected FXML controller: " + type.getName());
            }
            return controller;
        });
        final Scene scene = new Scene(loader.load(), 1080, 730);

        controller = loader.getController();
        scene.getStylesheets().add(css.toExternalForm());

        stage.setTitle("NFC Studio");
        stage.setResizable(false);
        stage.setScene(scene);
        stage.setOnCloseRequest(event ->
        {
            if (!controller.requestClose(stage::close))
            {
                event.consume();
            }
        });
        stage.show();
    }

    @Override
    public void stop()
    {
        if (controller != null)
        {
            controller.close();
        }
    }
}
