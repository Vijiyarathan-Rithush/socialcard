package com.rvi.presentation;

import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Objects;
import java.util.function.Consumer;

final class LinkRow
{
    private final HBox root;
    private final TextField field;
    private final Label number;
    private final Label name;
    private final Button remove;

    LinkRow(final String url, final Runnable changed, final Consumer<LinkRow> deleted)
    {
        try
        {
            final FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                    getClass().getResource("/presentation/link-row.fxml"), "Missing link-row.fxml resource."));
            root = loader.load();
            field = (TextField) loader.getNamespace().get("urlField");
            number = (Label) loader.getNamespace().get("number");
            name = (Label) loader.getNamespace().get("name");
            remove = (Button) loader.getNamespace().get("remove");
        }
        catch (IOException exception)
        {
            throw new UncheckedIOException(exception);
        }

        field.setText(url);
        updateName();
        field.textProperty().addListener((observable, previous, current) ->
        {
            updateName();
            changed.run();
        });
        remove.setOnAction(event -> deleted.accept(this));
    }

    HBox root()
    {
        return root;
    }

    String url()
    {
        return field.getText();
    }

    void number(final int index)
    {
        number.setText(String.format("%02d", index));
        field.setAccessibleText("HTTPS link " + index);
    }

    void focus()
    {
        field.requestFocus();
    }

    private void updateName()
    {
        name.setText("Link");

        try
        {
            final String host = URI.create(field.getText().strip()).getHost();

            if (host != null)
            {
                final String normalized = host.replaceFirst("^www\\.", "");
                name.setText(normalized);

                if (normalized.equalsIgnoreCase("github.com"))
                {
                    name.setText("GitHub");
                }
                else if (normalized.equalsIgnoreCase("instagram.com"))
                {
                    name.setText("Instagram");
                }
                else if (normalized.equalsIgnoreCase("linkedin.com"))
                {
                    name.setText("LinkedIn");
                }
            }
        }
        catch (IllegalArgumentException exception)
        {
            name.setText("Link");
        }
    }
}
