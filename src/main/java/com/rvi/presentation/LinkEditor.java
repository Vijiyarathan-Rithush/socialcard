package com.rvi.presentation;

import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

final class LinkEditor
{
    private final VBox container;
    private final Runnable changed;
    private final List<LinkRow> rows = new ArrayList<>();

    LinkEditor(final VBox container, final Runnable changed)
    {
        this.container = container;
        this.changed = changed;
    }

    List<String> urls()
    {
        return rows.stream().map(LinkRow::url).toList();
    }

    void add()
    {
        final LinkRow row = create("");
        rows.add(row);
        container.getChildren().add(row.root());
        notifyChanged();
        row.focus();
    }

    void replace(final List<String> urls)
    {
        // Construct first so a resource-loading error does not discard existing input.
        final List<LinkRow> replacement = urls.stream().map(this::create).toList();
        rows.clear();
        rows.addAll(replacement);
        container.getChildren().setAll(rows.stream().map(LinkRow::root).toList());
        notifyChanged();
    }

    private LinkRow create(final String url)
    {
        return new LinkRow(url, this::notifyChanged, row ->
        {
            rows.remove(row);
            container.getChildren().remove(row.root());
            notifyChanged();
        });
    }

    private void notifyChanged()
    {
        for (int index = 0; index < rows.size(); index++)
        {
            rows.get(index).number(index + 1);
        }
        changed.run();
    }
}
