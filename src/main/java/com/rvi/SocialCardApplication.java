package com.rvi;

import com.rvi.presentation.MainController;
import com.rvi.service.BatchService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public final class SocialCardApplication extends Application
{
    @Override
    public void start(final Stage stage) throws Exception
    {
        final var controller = new MainController(new BatchService());
        final var loader = new FXMLLoader(SocialCardApplication.class.getResource("/com/rvi/main.fxml"));
        loader.setControllerFactory(type -> controller);
        final VBox root = loader.load();
        stage.setScene(new Scene(root));
        stage.setTitle("SocialCard");
        stage.setMinWidth(820);
        stage.setMinHeight(560);
        stage.setOnCloseRequest(event -> {
            event.consume();
            stage.getScene().getRoot().setDisable(true);
            controller.close(() -> {
                stage.setOnCloseRequest(null);
                stage.close();
                Platform.exit();
            });
        });
        stage.show();
    }
}
