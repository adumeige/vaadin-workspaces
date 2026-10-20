package io.github.adumeige.vaadin.workspaces.cascade;

import io.github.adumeige.vaadin.workspaces.Rect;
import io.github.adumeige.vaadin.workspaces.WorkspaceLayout;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Columns side by side, each a {@link SlidingColumn}. The anchor row is y = 0: every column's
 * selected item is centred on it, so selections across columns are always aligned.
 *
 * <p>The selected item of a column may be taller than the others ({@link #selectedItemHeight}), for
 * instance to show a page render of the selected document; its neighbours make room above and
 * below. Item sizes can be set per column ({@link #columnItemSize}); columns without an override use
 * the layout-wide sizes.
 */
public final class CascadeLayout implements WorkspaceLayout<ColumnKey> {

    private IntFunction<SlidingColumn<?>> columns = i -> { throw new IllegalStateException("columns not set"); };
    private double columnWidth = 320;
    private double columnGap = 40;
    private double itemHeight = 120;
    private double itemGap = 16;
    private double selectedItemHeight = Double.NaN;
    private final Map<Integer, double[]> columnSizes = new HashMap<>();

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

    /** Height of each column's selected item; defaults to the item height. */
    public CascadeLayout selectedItemHeight(double height) {
        this.selectedItemHeight = height;
        return this;
    }

    /** Item and selected-item heights for one column, overriding the layout-wide sizes. */
    public CascadeLayout columnItemSize(int column, double itemHeight, double selectedItemHeight) {
        columnSizes.put(column, new double[] { itemHeight, selectedItemHeight });
        return this;
    }

    public double itemHeightOf(int column) {
        double[] s = columnSizes.get(column);
        return s != null ? s[0] : itemHeight;
    }

    public double selectedItemHeightOf(int column) {
        double[] s = columnSizes.get(column);
        if (s != null) return s[1];
        return Double.isNaN(selectedItemHeight) ? itemHeight : selectedItemHeight;
    }

    public double getColumnWidth() { return columnWidth; }
    public double getColumnGap()   { return columnGap; }
    public double getItemHeight()  { return itemHeight; }
    public double getItemGap()     { return itemGap; }
    public double rowStride()      { return itemHeight + itemGap; }

    public double columnX(int column) {
        return column * (columnWidth + columnGap);
    }

    /**
     * The anchor-row rectangle spanning the given columns, `rows` rows tall (the selected row plus
     * rows - 1 neighbours), centred on y = 0; tall enough for the tallest column in the range.
     */
    public Rect band(int fromColumn, int toColumn, int rows) {
        double h = 0;
        for (int c = fromColumn; c <= toColumn; c++) {
            h = Math.max(h, selectedItemHeightOf(c) + (rows - 1) * (itemHeightOf(c) + itemGap));
        }
        return new Rect(columnX(fromColumn), -h / 2, columnX(toColumn) + columnWidth - columnX(fromColumn), h);
    }

    @Override
    public Rect bounds(ColumnKey key) {
        int column = key.column();
        SlidingColumn<?> col = columns.apply(column);
        int index = col.items().indexOf(key.id());
        int offset = index < 0 ? 0 : col.offsetOf(index);
        double item = itemHeightOf(column);
        double selected = selectedItemHeightOf(column);
        double stride = item + itemGap;
        double y;
        if (offset == 0) y = -selected / 2;
        else if (offset < 0) y = -selected / 2 - itemGap - item + (offset + 1) * stride;
        else y = selected / 2 + itemGap + (offset - 1) * stride;
        return new Rect(columnX(column), y, columnWidth, offset == 0 ? selected : item);
    }
}
