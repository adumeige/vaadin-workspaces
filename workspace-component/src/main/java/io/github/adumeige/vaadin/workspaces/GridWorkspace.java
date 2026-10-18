package io.github.adumeige.vaadin.workspaces;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.shared.Registration;
import io.github.adumeige.vaadin.workspaces.layout.GridCell;
import io.github.adumeige.vaadin.workspaces.layout.GridLayout;

import java.util.List;
import java.util.Map;

/**
 * A leaf: {@link Workspace} + {@link GridLayout} + grid navigation behaviours.
 * Cells sit on a regular grid; arrow keys move one cell at a time, clamped to the occupied extent.
 */
public class GridWorkspace extends Workspace<GridCell> {

    private static final Map<String, Direction> ARROWS = Map.of(
            "ArrowLeft", Direction.LEFT, "ArrowRight", Direction.RIGHT,
            "ArrowUp", Direction.UP, "ArrowDown", Direction.DOWN);

    private final GridLayout grid;
    private GridCell current = new GridCell(0, 0);

    public GridWorkspace(int cellWidth, int cellHeight) {
        super(new GridLayout(cellWidth, cellHeight, 0));
        this.grid = (GridLayout) getLayout();
        addViewChangedListener(e -> {
            ViewState v = e.getView();
            current = grid.cellAt(v.centerX(), v.centerY());
            fireEvent(new CellFocusedEvent(this, e.isFromClient(), current.col(), current.row()));
        });
    }

    public GridWorkspace() {
        this(320, 240);
    }

    // ── Cells ────────────────────────────────────────────────────────────

    public GridWorkspace addCell(int col, int row, Component component) {
        addItem(new GridCell(col, row), component);
        return this;
    }

    public GridWorkspace removeCell(int col, int row) {
        removeItem(new GridCell(col, row));
        return this;
    }

    public GridWorkspace setCellSize(int width, int height) {
        grid.setCellSize(width, height);
        relayout();
        return this;
    }

    public GridWorkspace setCellMargin(int margin) {
        grid.setMargin(margin);
        relayout();
        return this;
    }

    public GridCell getCurrentCell() {
        return current;
    }

    // ── Navigation ───────────────────────────────────────────────────────

    public void fitToCell(int col, int row) {
        current = new GridCell(col, row);
        fitTo(current);
    }

    public void zoomToCell(int col, int row) {
        current = new GridCell(col, row);
        zoomTo(current);
    }

    public GridWorkspace setInitialCell(int col, int row) {
        current = new GridCell(col, row);
        setInitialFit(current);
        return this;
    }

    public void moveLeft()  { move(Direction.LEFT); }
    public void moveRight() { move(Direction.RIGHT); }
    public void moveUp()    { move(Direction.UP); }
    public void moveDown()  { move(Direction.DOWN); }

    /** Pan one cell in the direction at the current scale, clamped to the occupied grid extent. */
    public void move(Direction dir) {
        if (itemCount() == 0) return;
        int minCol = Integer.MAX_VALUE, maxCol = Integer.MIN_VALUE, minRow = Integer.MAX_VALUE, maxRow = Integer.MIN_VALUE;
        for (GridCell c : keys()) {
            minCol = Math.min(minCol, c.col()); maxCol = Math.max(maxCol, c.col());
            minRow = Math.min(minRow, c.row()); maxRow = Math.max(maxRow, c.row());
        }
        int col = Math.max(minCol, Math.min(maxCol, current.col() + dir.dx));
        int row = Math.max(minRow, Math.min(maxRow, current.row() + dir.dy));
        current = new GridCell(col, row);
        Rect r = grid.bounds(current);
        centerOn(r.centerX(), r.centerY());
    }

    /** Arrow keys on {@code scope} pan the workspace by one cell. Ignored while typing in a field. */
    public Registration bindArrowKeys(Component scope) {
        return KeyBindings.bind(scope, List.copyOf(ARROWS.keySet()), key -> move(ARROWS.get(key)));
    }

    // ── Events ───────────────────────────────────────────────────────────

    public static class CellFocusedEvent extends ComponentEvent<GridWorkspace> {
        private final int col;
        private final int row;

        public CellFocusedEvent(GridWorkspace source, boolean fromClient, int col, int row) {
            super(source, fromClient);
            this.col = col;
            this.row = row;
        }

        public int getCol() { return col; }
        public int getRow() { return row; }
        public GridCell getCell() { return new GridCell(col, row); }
    }

    /** Fired whenever the viewport settles, with the cell nearest the viewport centre. */
    public Registration addCellFocusedListener(ComponentEventListener<CellFocusedEvent> listener) {
        return addListener(CellFocusedEvent.class, listener);
    }
}
