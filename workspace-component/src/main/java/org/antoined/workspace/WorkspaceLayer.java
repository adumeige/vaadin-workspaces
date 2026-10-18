package org.antoined.workspace;

/**
 * A component added with {@link Workspace#addLayer} that wants to know when item bounds change,
 * typically because it anchors content to items.
 */
public interface WorkspaceLayer<K> {
    void onRelayout(Workspace<K> workspace);
}
