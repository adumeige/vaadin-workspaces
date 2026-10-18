package org.antoined.workspace.cascade;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * A vertical list with one selected element. Pure Java, no UI. The selected element defines
 * the column's slide: item {@code i} sits {@code i - selectedIndex} rows from the anchor row,
 * so the selection is always on the anchor and the column slides under it.
 *
 * Content and selection changes notify listeners; the leaf that owns the column reacts by
 * re-laying out and firing its own component events.
 *
 * @param <T> item type
 */
public final class SlidingColumn<T> {

    private List<T> items = List.of();
    private int selected = -1;
    private final List<BiConsumer<Optional<T>, Optional<T>>> selectionListeners = new ArrayList<>();
    private final List<Runnable> contentListeners = new ArrayList<>();

    // ── Content ──────────────────────────────────────────────────────────

    public List<T> items() { return items; }

    public int size() { return items.size(); }

    public boolean isEmpty() { return items.isEmpty(); }

    /**
     * Replace the content. Selection is kept by identity, else by nearest index, else the first
     * element, else nothing. Content listeners fire, then selection listeners if it changed.
     */
    public void setItems(List<T> newItems) {
        Optional<T> before = selected();
        int oldIndex = selected;
        items = List.copyOf(newItems);
        if (before.isPresent() && items.contains(before.get())) {
            selected = items.indexOf(before.get());
        } else if (items.isEmpty()) {
            selected = -1;
        } else {
            selected = Math.max(0, Math.min(items.size() - 1, oldIndex));
        }
        contentListeners.forEach(Runnable::run);
        Optional<T> after = selected();
        if (!before.equals(after)) {
            fireSelection(before, after);
        }
    }

    /** Append an item; selects it when the column was empty. */
    public void add(T item) {
        List<T> next = new ArrayList<>(items);
        next.add(item);
        setItems(next);
    }

    public void remove(T item) {
        List<T> next = new ArrayList<>(items);
        if (next.remove(item)) setItems(next);
    }

    // ── Selection ────────────────────────────────────────────────────────

    public Optional<T> selected() {
        return selected < 0 || selected >= items.size() ? Optional.empty() : Optional.of(items.get(selected));
    }

    public int selectedIndex() { return selected; }

    public boolean select(T item) {
        int idx = items.indexOf(item);
        return idx >= 0 && selectIndex(idx);
    }

    public boolean selectIndex(int index) {
        if (index < 0 || index >= items.size() || index == selected) return false;
        Optional<T> before = selected();
        selected = index;
        fireSelection(before, selected());
        return true;
    }

    public boolean next()     { return selectIndex(selected + 1); }
    public boolean previous() { return selectIndex(selected - 1); }
    public boolean first()    { return selectIndex(0); }
    public boolean last()     { return selectIndex(items.size() - 1); }

    /** Rows between an item and the anchor row: negative above, positive below. */
    public int offsetOf(int index) {
        return index - Math.max(0, selected);
    }

    // ── Listeners ────────────────────────────────────────────────────────

    public Runnable addSelectionListener(BiConsumer<Optional<T>, Optional<T>> listener) {
        selectionListeners.add(listener);
        return () -> selectionListeners.remove(listener);
    }

    public Runnable addContentListener(Runnable listener) {
        contentListeners.add(listener);
        return () -> contentListeners.remove(listener);
    }

    private void fireSelection(Optional<T> before, Optional<T> after) {
        new ArrayList<>(selectionListeners).forEach(l -> l.accept(before, after));
    }
}
