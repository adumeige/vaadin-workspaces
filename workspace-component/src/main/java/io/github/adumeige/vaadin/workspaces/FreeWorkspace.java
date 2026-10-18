package io.github.adumeige.vaadin.workspaces;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.shared.Registration;
import io.github.adumeige.vaadin.workspaces.layout.FreeLayout;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A leaf: {@link Workspace} + {@link FreeLayout} + spatial navigation through {@link Geometry}.
 * Items sit at caller-supplied rectangles; arrows move to the nearest item in that direction.
 */
public class FreeWorkspace<K> extends Workspace<K> {

    private static final Map<String, Direction> ARROWS = Map.of(
            "ArrowLeft", Direction.LEFT, "ArrowRight", Direction.RIGHT,
            "ArrowUp", Direction.UP, "ArrowDown", Direction.DOWN);

    private final FreeLayout<K> free;
    private K current;

    public FreeWorkspace() {
        super(new FreeLayout<>());
        this.free = (FreeLayout<K>) getLayout();
        addViewChangedListener(e -> {
            ViewState v = e.getView();
            Geometry.nearest(v.centerX(), v.centerY(), keys(), free::bounds).ifPresent(k -> {
                current = k;
                fireEvent(new ItemFocusedEvent<>(this, e.isFromClient(), k));
            });
        });
    }

    public FreeWorkspace<K> place(K key, Rect rect, Component component) {
        if (hasItem(key)) removeItem(key);
        free.place(key, rect);
        addItem(key, component);
        if (current == null) current = key;
        return this;
    }

    public FreeWorkspace<K> place(K key, double x, double y, double width, double height, Component component) {
        return place(key, new Rect(x, y, width, height), component);
    }

    @Override
    public FreeWorkspace<K> removeItem(K key) {
        super.removeItem(key);
        free.remove(key);
        if (key.equals(current)) current = keys().stream().findFirst().orElse(null);
        return this;
    }

    public Optional<K> getCurrent() {
        return Optional.ofNullable(current);
    }

    public void goTo(K key) {
        if (!hasItem(key)) return;
        current = key;
        fitTo(key);
    }

    /** Move to the nearest item in the direction, if any. */
    public void move(Direction dir) {
        if (current == null) return;
        Geometry.neighbour(current, dir, keys(), free::bounds).ifPresent(this::goTo);
    }

    public Registration bindArrowKeys(Component scope) {
        return KeyBindings.bind(scope, List.copyOf(ARROWS.keySet()), key -> move(ARROWS.get(key)));
    }

    public static class ItemFocusedEvent<K> extends ComponentEvent<FreeWorkspace<K>> {
        private final K key;

        public ItemFocusedEvent(FreeWorkspace<K> source, boolean fromClient, K key) {
            super(source, fromClient);
            this.key = key;
        }

        public K getKey() { return key; }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Registration addItemFocusedListener(ComponentEventListener<ItemFocusedEvent<K>> listener) {
        return addListener((Class) ItemFocusedEvent.class, (ComponentEventListener) listener);
    }
}
