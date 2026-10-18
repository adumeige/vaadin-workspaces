package io.github.adumeige.vaadin.workspaces.cascade;

import io.github.adumeige.vaadin.workspaces.Rect;
import io.github.adumeige.vaadin.workspaces.WorkspaceLayout;

import java.util.function.IntFunction;

/**
 * Columns side by side, each a {@link SlidingColumn}. The anchor row is y = 0: every column's
 * selected item is centred on it, so selections across columns are always aligned.
 */
public final class CascadeLayout implements WorkspaceLayout<ColumnKey> {

    private IntFunction<SlidingColumn<?>> columns = i -> { throw new IllegalStateException("columns not set"); };
    private double columnWidth = 320;
    private double columnGap = 40;
    private double itemHeight = 120;
    private double itemGap = 16;

    public CascadeLayout() {
    }

    public CascadeLayout(IntFunction<SlidingColumn<?>> columns) {
        this.columns = columns;
    }

    /** The leaf sets this once its columns exist. */
    public void setColumns(IntFunction<SlidingColumn<?>> columns) {
        this.columns = columns;
    }

    public CascadeLayout columnSize(double width, double gap) {
        this.columnWidth = width;
        this.columnGap = gap;
        return this;
    }

    public CascadeLayout itemSize(double height, double gap) {
        this.itemHeight = height;
        this.itemGap = gap;
        return this;
    }

    public double getColumnWidth() { return columnWidth; }
    public double getColumnGap()   { return columnGap; }
    public double getItemHeight()  { return itemHeight; }
    public double getItemGap()     { return itemGap; }
    public double rowStride()      { return itemHeight + itemGap; }

    public double columnX(int column) {
        return column * (columnWidth + columnGap);
    }

    /** The anchor-row rectangle spanning the given columns, `rows` rows tall, centred on y = 0. */
    public Rect band(int fromColumn, int toColumn, int rows) {
        double h = rows * rowStride() - itemGap;
        return new Rect(columnX(fromColumn), -h / 2, columnX(toColumn) + columnWidth - columnX(fromColumn), h);
    }

    @Override
    public Rect bounds(ColumnKey key) {
        SlidingColumn<?> col = columns.apply(key.column());
        int index = col.items().indexOf(key.id());
        int offset = index < 0 ? 0 : col.offsetOf(index);
        return new Rect(columnX(key.column()), offset * rowStride() - itemHeight / 2, columnWidth, itemHeight);
    }
}
