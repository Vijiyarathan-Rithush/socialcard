package com.rvi.presentation;

import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;

final class LinkRow
{
    private final HBox root;
    private final TextField field;
    private final Label number;
    private final Label name;
    private final Button remove;

    LinkRow(final String url, final Runnable changed, final Runnable deleted)
    {
        try
        {
            final FXMLLoader loader = new FXMLLoader(getClass().getResource("link-row.fxml"));
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
        remove.setOnAction(event -> deleted.run());
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
        field.setAccessibleText("HTTPS-Link " + index);
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
