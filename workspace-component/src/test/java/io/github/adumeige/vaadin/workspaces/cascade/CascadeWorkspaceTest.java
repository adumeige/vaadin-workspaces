package io.github.adumeige.vaadin.workspaces.cascade;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CascadeWorkspaceTest {

    private final CascadeWorkspace ws = new CascadeWorkspace();
    private final SlidingColumn<String> folders = ws.addColumn();
    private final SlidingColumn<Integer> sizes = ws.addColumn();
    private final SlidingColumn<String> labels = ws.addColumn();

    CascadeWorkspaceTest() {
        ws.derive(folders, sizes, f -> List.of(f.length(), f.length() * 2));
        ws.derive(sizes, labels, n -> List.of("n=" + n));
    }

    @Test
    void derivationsCascadeThroughColumns() {
        folders.setItems(List.of("ab", "cde"));
        assertEquals(List.of(2, 4), sizes.items());
        assertEquals(List.of("n=2"), labels.items());
        folders.select("cde");
        assertEquals(List.of(3, 6), sizes.items());
        assertEquals(List.of("n=3"), labels.items());
        sizes.select(6);
        assertEquals(List.of("n=6"), labels.items());
    }

    @Test
    void openAddsSelectsAndActivates() {
        folders.setItems(List.of("ab"));
        ws.setActiveColumn(2);
        ws.open(folders, "xyz");
        assertEquals(List.of("ab", "xyz"), folders.items());
        assertEquals(Optional.of("xyz"), folders.selected());
        assertEquals(0, ws.getActiveColumn());
        assertEquals(2, ws.keys().stream().filter(k -> k.column() == 0).count(), "every folder is rendered");
    }

    @Test
    void renderWindowBoundsRendering() {
        ws.setRenderWindow(1);
        folders.setItems(List.of("a", "b", "c", "d", "e"));
        folders.select("c");
        long rendered = ws.keys().stream().filter(k -> k.column() == 0).count();
        assertEquals(3, rendered);
    }

    @Test
    void foreignColumnsAreRejected() {
        SlidingColumn<String> other = new SlidingColumn<>();
        assertThrows(IllegalArgumentException.class, () -> ws.derive(other, labels, s -> List.of()));
        assertThrows(IllegalArgumentException.class, () -> ws.setActiveColumn(other));
        assertTrue(ws.columnCount() == 3);
    }
}
