package io.github.adumeige.vaadin.workspaces.cascade;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.shared.Registration;
import io.github.adumeige.vaadin.workspaces.ComponentProvider;
import io.github.adumeige.vaadin.workspaces.KeyBindings;
import io.github.adumeige.vaadin.workspaces.Workspace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * A leaf: {@link Workspace} + {@link CascadeLayout} + any number of {@link SlidingColumn}s + behaviours.
 *
 * Columns stand side by side. Each has one selection, which always sits on the shared anchor row,
 * so the selections of every column are aligned. Moving a column's selection slides that column.
 * A column can be <em>derived</em> from another: whenever the source column's selection changes,
 * the derived column's content is recomputed from it. Selection cascades rightward, or in any
 * direction the derivations describe.
 *
 * <pre>
 *   CascadeWorkspace ws = new CascadeWorkspace();
 *   SlidingColumn&lt;Folder&gt; folders = ws.addColumn(f -&gt; new Div(f.name()));
 *   SlidingColumn&lt;File&gt;   files   = ws.addColumn(f -&gt; new Div(f.name()));
 *   ws.derive(folders, files, Folder::files);
 * </pre>
 *
 * Left/Right change the active column, Up/Down move its selection, Enter activates the selection.
 * Inspired by Miller columns, but every column keeps its selection on the anchor row instead of
 * scrolling independently.
 */
@CssImport("./cascade.css")
public class CascadeWorkspace extends Workspace<ColumnKey> {

    /** What the viewport fits after each change. {@code MANUAL} leaves the viewport to the application. */
    public enum ViewMode { ALL_COLUMNS, ACTIVE_COLUMN, MANUAL }

    private final List<SlidingColumn<?>> columns = new ArrayList<>();
    private final Map<SlidingColumn<?>, ComponentProvider<?>> renderers = new HashMap<>();
    private final CascadeLayout cascade;

    private int activeColumn = 0;
    private int renderWindow = Integer.MAX_VALUE / 4;
    private int visibleRows = 3;
    private double fitPadding = 40;
    private ViewMode viewMode = ViewMode.ALL_COLUMNS;
    private boolean syncing = false;

    public CascadeWorkspace() {
        super(new CascadeLayout());
        this.cascade = (CascadeLayout) super.getLayout();
        this.cascade.setColumns(columns::get);
        getElement().getClassList().add("cascade");
        setAnimateItems(true);
        setDraggingEnabled(false);
        setZoomingEnabled(false);
    }

    @Override
    public CascadeLayout getLayout() {
        return cascade;
    }

    // ── Columns ──────────────────────────────────────────────────────────

    /** Append a column rendered with {@code toString()}. */
    public <T> SlidingColumn<T> addColumn() {
        return addColumn(t -> new Div(String.valueOf(t)));
    }

    /** Append a column; the returned handle is how the application fills, selects and wires it. */
    public <T> SlidingColumn<T> addColumn(ComponentProvider<T> renderer) {
        SlidingColumn<T> column = new SlidingColumn<>();
        final int col = columns.size();
        columns.add(column);
        renderers.put(column, renderer);
        column.addContentListener(() -> {
            sync();
            fireEvent(new ContentChangedEvent(this, col));
        });
        column.addSelectionListener((before, after) -> {
            sync();
            fireEvent(new SelectionChangedEvent(this, col, after.orElse(null)));
            fireEvent(new SlideEvent(this, col, column.selectedIndex()));
        });
        return column;
    }

    public <T> CascadeWorkspace setRenderer(SlidingColumn<T> column, ComponentProvider<T> renderer) {
        requireColumn(column);
        renderers.put(column, renderer);
        return this;
    }

    /**
     * Keep {@code to} filled from the selection of {@code from}: on every selection change of the
     * source, the target's items become {@code fn(selected)}, or nothing when the source is empty.
     * The derivation is applied immediately.
     */
    public <S, T> CascadeWorkspace derive(SlidingColumn<S> from, SlidingColumn<T> to, Function<S, List<T>> fn) {
        requireColumn(from);
        requireColumn(to);
        from.addSelectionListener((before, after) -> to.setItems(after.map(fn).orElse(List.of())));
        to.setItems(from.selected().map(fn).orElse(List.of()));
        return this;
    }

    public int columnCount() { return columns.size(); }

    public SlidingColumn<?> column(int index) { return columns.get(index); }

    public int indexOf(SlidingColumn<?> column) {
        int i = columns.indexOf(column);
        if (i < 0) throw new IllegalArgumentException("column does not belong to this workspace");
        return i;
    }

    public int getActiveColumn() { return activeColumn; }

    public SlidingColumn<?> activeColumn() { return columns.get(activeColumn); }

    public void setActiveColumn(int column) {
        int target = Math.max(0, Math.min(columns.size() - 1, column));
        if (target == activeColumn) return;
        activeColumn = target;
        sync();
        fireEvent(new ColumnActivatedEvent(this, activeColumn));
    }

    public void setActiveColumn(SlidingColumn<?> column) {
        setActiveColumn(indexOf(column));
    }

    private void requireColumn(SlidingColumn<?> column) {
        indexOf(column);
    }

    // ── Configuration ────────────────────────────────────────────────────

    /** Only items within this many rows of each column's selection are rendered. */
    public CascadeWorkspace setRenderWindow(int rows) { this.renderWindow = rows; return this; }

    /** Rows the viewport fits vertically around the anchor row. */
    public CascadeWorkspace setVisibleRows(int rows) { this.visibleRows = rows; return this; }

    public CascadeWorkspace setViewMode(ViewMode mode) { this.viewMode = mode; sync(); return this; }

    public CascadeWorkspace setFitPadding(double padding) { this.fitPadding = padding; return this; }

    /** Render (or re-render) everything. Call once after configuration. */
    public CascadeWorkspace refresh() {
        sync();
        return this;
    }

    // ── Items ────────────────────────────────────────────────────────────

    /** Add the item to the column if new, select it, and make that column active. Fires {@link ItemOpenedEvent}. */
    public <T> void open(SlidingColumn<T> column, T item) {
        int col = indexOf(column);
        if (!column.items().contains(item)) column.add(item);
        column.select(item);
        setActiveColumn(col);
        fireEvent(new ItemOpenedEvent(this, col, item));
    }

    public <T> void close(SlidingColumn<T> column, T item) {
        requireColumn(column);
        column.remove(item);
    }

    public Optional<?> selected(int column) { return columns.get(column).selected(); }

    // ── Navigation ───────────────────────────────────────────────────────

    public void moveLeft()  { setActiveColumn(activeColumn - 1); }
    public void moveRight() { setActiveColumn(activeColumn + 1); }
    public void moveUp()    { if (!columns.isEmpty()) activeColumn().previous(); }
    public void moveDown()  { if (!columns.isEmpty()) activeColumn().next(); }

    /** Activate the active column's selection: fires {@link ItemActivatedEvent}. */
    public void activate() {
        if (columns.isEmpty()) return;
        activeColumn().selected().ifPresent(item ->
                fireEvent(new ItemActivatedEvent(this, activeColumn, item)));
    }

    /** Arrows, Home, End and Enter on the scope. Ignored while typing in a field. */
    public Registration bindKeys(Component scope) {
        return KeyBindings.bind(scope, List.of("ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown", "Home", "End", "Enter"), key -> {
            switch (key) {
                case "ArrowLeft" -> moveLeft();
                case "ArrowRight" -> moveRight();
                case "ArrowUp" -> moveUp();
                case "ArrowDown" -> moveDown();
                case "Home" -> { if (!columns.isEmpty()) activeColumn().first(); }
                case "End" -> { if (!columns.isEmpty()) activeColumn().last(); }
                case "Enter" -> activate();
                default -> { }
            }
        });
    }

    // ── Rendering ────────────────────────────────────────────────────────

    private void sync() {
        if (syncing) return; // listeners triggered during a sync re-enter here; one pass suffices
        syncing = true;
        try {
            doSync();
        } finally {
            syncing = false;
        }
    }

    private void doSync() {
        Set<ColumnKey> wanted = new LinkedHashSet<>();
        for (int col = 0; col < columns.size(); col++) {
            SlidingColumn<?> column = columns.get(col);
            int sel = Math.max(0, column.selectedIndex());
            List<?> items = column.items();
            for (int i = 0; i < items.size(); i++) {
                if (Math.abs(i - sel) > renderWindow) continue;
                wanted.add(new ColumnKey(col, items.get(i)));
            }
        }
        for (ColumnKey key : new ArrayList<>(keys())) {
            if (!wanted.contains(key)) removeItem(key);
        }
        for (ColumnKey key : wanted) {
            if (!hasItem(key)) materialise(key);
        }
        relayout(wanted);
        for (ColumnKey key : wanted) {
            itemElement(key).ifPresent(el -> tag(el, key));
        }
        if (columns.isEmpty()) return;
        switch (viewMode) {
            case ALL_COLUMNS -> fitRect(cascade.band(0, columns.size() - 1, visibleRows), fitPadding);
            case ACTIVE_COLUMN -> fitRect(cascade.band(activeColumn, activeColumn, visibleRows), fitPadding);
            case MANUAL -> { }
        }
    }

    @SuppressWarnings("unchecked")
    private void materialise(ColumnKey key) {
        SlidingColumn<?> column = columns.get(key.column());
        ComponentProvider<Object> renderer = (ComponentProvider<Object>) renderers.get(column);
        addItem(key, renderer.create(key.id()));
        itemElement(key).ifPresent(el -> {
            el.getClassList().add("cascade-item");
            el.getClassList().add("cascade-col-" + key.column());
            el.addEventListener("click", e -> {
                setActiveColumn(key.column());
                selectRaw(column, key.id());
            });
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> void selectRaw(SlidingColumn<T> column, Object id) {
        column.select((T) id);
    }

    private void tag(Element el, ColumnKey key) {
        SlidingColumn<?> column = columns.get(key.column());
        boolean selected = column.selected().map(s -> s.equals(key.id())).orElse(false);
        boolean active = key.column() == activeColumn;
        el.getClassList().set("cascade-selected", selected);
        el.getClassList().set("cascade-active-col", active);
        el.setAttribute("aria-selected", String.valueOf(selected));
        if (selected && active) el.setAttribute("aria-current", "true");
        else el.removeAttribute("aria-current");
    }

    // ── Events ───────────────────────────────────────────────────────────

    /** Base of every cascade event: the column it concerns, by index and by handle. */
    public abstract static class ColumnEvent extends ComponentEvent<CascadeWorkspace> {
        private final int column;
        protected ColumnEvent(CascadeWorkspace source, int column) { super(source, false); this.column = column; }
        public int getColumn() { return column; }
        public SlidingColumn<?> column() { return getSource().column(column); }
    }

    public static class ColumnActivatedEvent extends ColumnEvent {
        public ColumnActivatedEvent(CascadeWorkspace source, int column) { super(source, column); }
    }

    public static class SelectionChangedEvent extends ColumnEvent {
        private final Object item;
        public SelectionChangedEvent(CascadeWorkspace source, int column, Object item) { super(source, column); this.item = item; }
        /** Null when the column is empty. */
        public Object getItem() { return item; }
    }

    /** Fired when a column slides, that is whenever its selected index changes. */
    public static class SlideEvent extends ColumnEvent {
        private final int selectedIndex;
        public SlideEvent(CascadeWorkspace source, int column, int selectedIndex) { super(source, column); this.selectedIndex = selectedIndex; }
        public int getSelectedIndex() { return selectedIndex; }
    }

    public static class ContentChangedEvent extends ColumnEvent {
        public ContentChangedEvent(CascadeWorkspace source, int column) { super(source, column); }
    }

    public static class ItemActivatedEvent extends ColumnEvent {
        private final Object item;
        public ItemActivatedEvent(CascadeWorkspace source, int column, Object item) { super(source, column); this.item = item; }
        public Object getItem() { return item; }
    }

    /** Fired by {@link #open(SlidingColumn, Object)}. */
    public static class ItemOpenedEvent extends ColumnEvent {
        private final Object item;
        public ItemOpenedEvent(CascadeWorkspace source, int column, Object item) { super(source, column); this.item = item; }
        public Object getItem() { return item; }
    }

    public Registration addColumnActivatedListener(ComponentEventListener<ColumnActivatedEvent> l) {
        return addListener(ColumnActivatedEvent.class, l);
    }

    public Registration addSelectionChangedListener(ComponentEventListener<SelectionChangedEvent> l) {
        return addListener(SelectionChangedEvent.class, l);
    }

    public Registration addSlideListener(ComponentEventListener<SlideEvent> l) {
        return addListener(SlideEvent.class, l);
    }

    public Registration addContentChangedListener(ComponentEventListener<ContentChangedEvent> l) {
        return addListener(ContentChangedEvent.class, l);
    }

    public Registration addItemActivatedListener(ComponentEventListener<ItemActivatedEvent> l) {
        return addListener(ItemActivatedEvent.class, l);
    }

    public Registration addItemOpenedListener(ComponentEventListener<ItemOpenedEvent> l) {
        return addListener(ItemOpenedEvent.class, l);
    }
}
