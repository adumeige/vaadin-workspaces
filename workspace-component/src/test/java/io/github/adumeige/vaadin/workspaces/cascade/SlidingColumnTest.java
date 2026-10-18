package io.github.adumeige.vaadin.workspaces.cascade;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SlidingColumnTest {

    @Test
    void firstContentSelectsFirstItemAndNotifies() {
        SlidingColumn<String> c = new SlidingColumn<>();
        List<String> seen = new ArrayList<>();
        c.addSelectionListener((b, a) -> seen.add(b.orElse("-") + ">" + a.orElse("-")));
        c.setItems(List.of("a", "b", "c"));
        assertEquals(Optional.of("a"), c.selected());
        assertEquals(List.of("->a"), seen);
    }

    @Test
    void navigationStopsAtBoundaries() {
        SlidingColumn<String> c = new SlidingColumn<>();
        c.setItems(List.of("a", "b"));
        assertTrue(c.next());
        assertFalse(c.next());
        assertEquals(1, c.selectedIndex());
        assertTrue(c.previous());
        assertFalse(c.previous());
        assertTrue(c.last());
        assertTrue(c.first());
    }

    @Test
    void offsetIsRelativeToSelection() {
        SlidingColumn<String> c = new SlidingColumn<>();
        c.setItems(List.of("a", "b", "c"));
        c.selectIndex(2);
        assertEquals(-2, c.offsetOf(0));
        assertEquals(0, c.offsetOf(2));
    }

    @Test
    void contentChangeKeepsSelectionByIdentityThenIndex() {
        SlidingColumn<String> c = new SlidingColumn<>();
        c.setItems(List.of("a", "b", "c"));
        c.select("b");
        c.setItems(List.of("c", "b", "a"));
        assertEquals(Optional.of("b"), c.selected());
        c.setItems(List.of("x", "y"));
        assertEquals(Optional.of("y"), c.selected(), "nearest index to old index 1");
        c.setItems(List.of());
        assertTrue(c.selected().isEmpty());
        assertEquals(-1, c.selectedIndex());
    }

    @Test
    void addAndRemove() {
        SlidingColumn<String> c = new SlidingColumn<>();
        int[] content = {0};
        c.addContentListener(() -> content[0]++);
        c.add("a");
        assertEquals(Optional.of("a"), c.selected());
        c.add("b");
        assertEquals(Optional.of("a"), c.selected(), "adding does not steal selection");
        c.remove("a");
        assertEquals(Optional.of("b"), c.selected());
        assertEquals(3, content[0]);
    }
}
