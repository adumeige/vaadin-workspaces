package org.antoined.workspace.layout;

import org.antoined.workspace.Rect;
import org.antoined.workspace.WorkspaceLayout;

/**
 * Fixed-size cells on a regular grid with a uniform margin between them.
 */
public class GridLayout implements WorkspaceLayout<GridCell> {

    private int cellWidth;
    private int cellHeight;
    private int margin;

    public GridLayout(int cellWidth, int cellHeight, int margin) {
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.margin = margin;
    }

    @Override
    public Rect bounds(GridCell cell) {
        return new Rect(
                cell.col() * (double) (cellWidth + margin),
                cell.row() * (double) (cellHeight + margin),
                cellWidth, cellHeight);
    }

    /** Inverse mapping: the cell whose centre is nearest to the given point. Exact for a grid. */
    public GridCell cellAt(double x, double y) {
        int col = (int) Math.round((x - cellWidth / 2.0) / (cellWidth + margin));
        int row = (int) Math.round((y - cellHeight / 2.0) / (cellHeight + margin));
        return new GridCell(col, row);
    }

    public int getCellWidth()  { return cellWidth; }
    public int getCellHeight() { return cellHeight; }
    public int getMargin()     { return margin; }

    public void setCellSize(int width, int height) {
        this.cellWidth = width;
        this.cellHeight = height;
    }

    public void setMargin(int margin) {
        this.margin = margin;
    }
}
