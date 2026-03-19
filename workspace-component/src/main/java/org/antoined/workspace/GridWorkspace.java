package org.antoined.workspace;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Focusable;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.DomEvent;
import com.vaadin.flow.component.EventData;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.shared.Registration;

import java.util.HashMap;
import java.util.Map;

@Tag("grid-workspace-view")
@JsModule("./grid-workspace-view.ts")
public class GridWorkspace extends Component implements Focusable<GridWorkspace> {

    private int cellWidth;
    private int cellHeight;
    private int cellMargin = 0;
    private final Map<String, Div> cells = new HashMap<>();

    public GridWorkspace(int cellWidth, int cellHeight) {
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        getElement().getStyle()
                .set("display", "block")
                .set("position", "relative")
                .set("overflow", "hidden");
        getElement().setProperty("cellWidth", cellWidth);
        getElement().setProperty("cellHeight", cellHeight);
        getElement().setProperty("cellMargin", cellMargin);
        getElement().setProperty("draggingEnabled", true);
        getElement().setProperty("zoomingEnabled", true);
        getElement().setAttribute("tabindex", "0");
    }

    public GridWorkspace() {
        this(320, 240);
    }

    public GridWorkspace addCell(int col, int row, Component component) {
        String key = col + "," + row;
        // Remove existing cell at this position if any
        removeCell(col, row);

        Div wrapper = new Div(component);
        wrapper.getElement().setAttribute("data-cell", "true");
        wrapper.getElement().setAttribute("data-col", String.valueOf(col));
        wrapper.getElement().setAttribute("data-row", String.valueOf(row));
        wrapper.getStyle()
                .set("position", "absolute")
                .set("left", (col * (cellWidth + cellMargin)) + "px")
                .set("top", (row * (cellHeight + cellMargin)) + "px")
                .set("width", cellWidth + "px")
                .set("height", cellHeight + "px")
                .set("overflow", "hidden")
                .set("box-sizing", "border-box");

        cells.put(key, wrapper);
        getElement().appendChild(wrapper.getElement());
        return this;
    }

    public GridWorkspace removeCell(int col, int row) {
        String key = col + "," + row;
        Div existing = cells.remove(key);
        if (existing != null) {
            existing.getElement().removeFromParent();
        }
        return this;
    }

    public void fitToCell(int col, int row) {
        getElement().callJsFunction("fitToCell", (double) col, (double) row);
    }

    public void zoomToCell(int col, int row) {
        getElement().callJsFunction("zoomToCell", (double) col, (double) row);
    }

    public void fitAll() {
        getElement().callJsFunction("fitAll");
    }

    public GridWorkspace setInitialCell(int col, int row) {
        getElement().setProperty("initialCol", col);
        getElement().setProperty("initialRow", row);
        return this;
    }

    public GridWorkspace setInitialFitAll() {
        getElement().setProperty("initialFitAll", true);
        return this;
    }

    public GridWorkspace setDraggingEnabled(boolean enabled) {
        getElement().setProperty("draggingEnabled", enabled);
        return this;
    }

    public GridWorkspace setZoomingEnabled(boolean enabled) {
        getElement().setProperty("zoomingEnabled", enabled);
        return this;
    }

    public void moveLeft()  { getElement().callJsFunction("moveLeft"); }
    public void moveRight() { getElement().callJsFunction("moveRight"); }
    public void moveUp()    { getElement().callJsFunction("moveUp"); }
    public void moveDown()  { getElement().callJsFunction("moveDown"); }

    /**
     * Binds arrow keys on {@code scope} to pan the workspace by one cell step.
     * Ignores key events when focus is on an interactive element (input, textarea, etc.).
     * Call {@link #unbindArrowKeys(Component)} to remove the binding.
     */
    public void bindArrowKeys(Component scope) {
        scope.getElement().executeJs("""
            const ws = $0;
            const handler = (e) => {
                if (!e.key.startsWith('Arrow')) return;
                if (ws.deepActiveIsInteractive()) return;
                e.preventDefault();
                if      (e.key === 'ArrowLeft')  ws.moveLeft();
                else if (e.key === 'ArrowRight') ws.moveRight();
                else if (e.key === 'ArrowUp')    ws.moveUp();
                else if (e.key === 'ArrowDown')  ws.moveDown();
            };
            this._wsArrowHandler = handler;
            this.addEventListener('keydown', handler);
        """, getElement());
    }

    public void unbindArrowKeys(Component scope) {
        scope.getElement().executeJs("""
            if (this._wsArrowHandler) {
                this.removeEventListener('keydown', this._wsArrowHandler);
                delete this._wsArrowHandler;
            }
        """);
    }

    public GridWorkspace setCellSize(int width, int height) {
        this.cellWidth = width;
        this.cellHeight = height;
        getElement().setProperty("cellWidth", width);
        getElement().setProperty("cellHeight", height);
        repositionAll();
        return this;
    }

    public GridWorkspace setCellMargin(int margin) {
        this.cellMargin = margin;
        getElement().setProperty("cellMargin", margin);
        repositionAll();
        return this;
    }

    private void repositionAll() {
        for (Map.Entry<String, Div> entry : cells.entrySet()) {
            String[] parts = entry.getKey().split(",");
            int col = Integer.parseInt(parts[0]);
            int row = Integer.parseInt(parts[1]);
            entry.getValue().getStyle()
                    .set("left", (col * (cellWidth + cellMargin)) + "px")
                    .set("top", (row * (cellHeight + cellMargin)) + "px")
                    .set("width", cellWidth + "px")
                    .set("height", cellHeight + "px");
        }
    }

    @DomEvent("workspace-cell-focused")
    public static class CellFocusedEvent extends ComponentEvent<GridWorkspace> {
        private final int col;
        private final int row;

        public CellFocusedEvent(GridWorkspace source, boolean fromClient,
                                @EventData("event.detail.col") int col,
                                @EventData("event.detail.row") int row) {
            super(source, fromClient);
            this.col = col;
            this.row = row;
        }

        public int getCol() { return col; }
        public int getRow() { return row; }
    }

    public Registration addCellFocusedListener(ComponentEventListener<CellFocusedEvent> listener) {
        return addListener(CellFocusedEvent.class, listener);
    }

    public GridWorkspace setSize(String width, String height) {
        getElement().getStyle()
                .set("width", width)
                .set("height", height);
        return this;
    }
}
