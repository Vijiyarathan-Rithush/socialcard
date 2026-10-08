package com.rvi.presentation;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class NfcStudioApplication extends Application
{
    private StudioController controller;

    @Override
    public void start(final Stage stage) throws Exception
    {
        final FXMLLoader loader = new FXMLLoader(getClass().getResource("studio.fxml"));
        final Scene scene = new Scene(loader.load(), 1080, 730);
        controller = loader.getController();
        scene.getStylesheets().add(getClass().getResource("studio.css").toExternalForm());
        stage.setTitle("NFC Studio");
        stage.setResizable(false);
        stage.setScene(scene);
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
