package org.antoined.workspace;

import com.vaadin.flow.component.Component;

/**
 * Creates the component shown for a key. Leaves use providers to materialise items lazily.
 */
@FunctionalInterface
public interface ComponentProvider<K> {
    Component create(K key);
}
