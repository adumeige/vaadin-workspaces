package io.github.adumeige.vaadin.workspaces.layout;

import io.github.adumeige.vaadin.workspaces.Rect;
import io.github.adumeige.vaadin.workspaces.WorkspaceLayout;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Explicit rectangles supplied by the caller for each key.
 */
public class FreeLayout<K> implements WorkspaceLayout<K> {

    private final Map<K, Rect> rects = new HashMap<>();

    public FreeLayout<K> place(K key, Rect rect) {
        rects.put(key, rect);
        return this;
    }

    public FreeLayout<K> place(K key, double x, double y, double width, double height) {
        return place(key, new Rect(x, y, width, height));
    }

    public Optional<Rect> remove(K key) {
        return Optional.ofNullable(rects.remove(key));
    }

    @Override
    public Rect bounds(K key) {
        Rect r = rects.get(key);
        if (r == null) {
            throw new IllegalArgumentException("No rectangle placed for key " + key);
        }
        return r;
    }
}
