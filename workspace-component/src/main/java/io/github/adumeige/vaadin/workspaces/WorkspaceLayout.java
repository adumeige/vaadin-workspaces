package io.github.adumeige.vaadin.workspaces;

import java.util.Collection;

/**
 * Maps item keys to rectangles on the flat workspace surface. A layout is pure geometry:
 * it has no notion of navigation, focus or neighbours. Those belong to the leaf component
 * that composes a layout with behaviours.
 *
 * @param <K> item key type
 */
@FunctionalInterface
public interface WorkspaceLayout<K> {

    /** Rectangle occupied by the item with the given key. */
    Rect bounds(K key);

    /** Union of the bounds of all given keys, or {@link Rect#EMPTY} if none. */
    default Rect extent(Collection<K> keys) {
        Rect result = null;
        for (K key : keys) {
            Rect r = bounds(key);
            result = result == null ? r : result.union(r);
        }
        return result == null ? Rect.EMPTY : result;
    }
}
