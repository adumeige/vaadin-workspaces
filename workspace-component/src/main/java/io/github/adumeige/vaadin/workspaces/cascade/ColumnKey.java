package io.github.adumeige.vaadin.workspaces.cascade;

/** An item on the cascade surface: which column, and which element of that column. */
public record ColumnKey(int column, Object id) {}
