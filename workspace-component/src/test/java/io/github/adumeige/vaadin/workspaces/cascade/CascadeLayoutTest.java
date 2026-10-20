package io.github.adumeige.vaadin.workspaces.cascade;

import io.github.adumeige.vaadin.workspaces.Rect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CascadeLayoutTest {

    private final SlidingColumn<String> c0 = new SlidingColumn<>();
    private final SlidingColumn<String> c1 = new SlidingColumn<>();
    private final CascadeLayout layout = new CascadeLayout(i -> i == 0 ? c0 : c1)
            .columnSize(100, 20).itemSize(50, 10);

    CascadeLayoutTest() {
        c0.setItems(List.of("p"));
        c1.setItems(List.of("a", "b", "c"));
    }

    @Test
    void selectedItemsOfAllColumnsShareTheAnchorRow() {
        c1.select("c");
        Rect p = layout.bounds(new ColumnKey(0, "p"));
        Rect c = layout.bounds(new ColumnKey(1, "c"));
        assertEquals(p.y(), c.y());
        assertEquals(-25, p.y());
        assertEquals(0, p.centerY());
        assertEquals(120, c.x());
    }

    @Test
    void columnSlidesWithSelection() {
        assertEquals(new Rect(120, -25, 100, 50), layout.bounds(new ColumnKey(1, "a")));
        assertEquals(new Rect(120, 35, 100, 50), layout.bounds(new ColumnKey(1, "b")));
        c1.select("b");
        assertEquals(new Rect(120, -85, 100, 50), layout.bounds(new ColumnKey(1, "a")));
        assertEquals(new Rect(120, -25, 100, 50), layout.bounds(new ColumnKey(1, "b")));
    }

    @Test
    void bandSpansColumnsAndRows() {
        assertEquals(new Rect(0, -85, 220, 170), layout.band(0, 1, 3));
        assertEquals(new Rect(120, -25, 100, 50), layout.band(1, 1, 1));
    }

    @Test
    void selectedItemCanBeTallerAndNeighboursMakeRoom() {
        layout.selectedItemHeight(200);
        c1.select("b");
        assertEquals(new Rect(120, -100, 100, 200), layout.bounds(new ColumnKey(1, "b")));
        assertEquals(new Rect(120, -160, 100, 50), layout.bounds(new ColumnKey(1, "a")), "above: gap 10 above the selected item");
        assertEquals(new Rect(120, 110, 100, 50), layout.bounds(new ColumnKey(1, "c")), "below: gap 10 below the selected item");
        assertEquals(new Rect(0, -100, 100, 200), layout.bounds(new ColumnKey(0, "p")), "selections still share the anchor row centre");
        assertEquals(new Rect(0, -160, 220, 320), layout.band(0, 1, 3));
    }

    @Test
    void sizesCanBeSetPerColumn() {
        layout.columnItemSize(1, 40, 300);
        c1.select("a");
        assertEquals(new Rect(120, -150, 100, 300), layout.bounds(new ColumnKey(1, "a")));
        assertEquals(new Rect(120, 160, 100, 40), layout.bounds(new ColumnKey(1, "b")));
        assertEquals(new Rect(120, 210, 100, 40), layout.bounds(new ColumnKey(1, "c")));
        assertEquals(new Rect(0, -25, 100, 50), layout.bounds(new ColumnKey(0, "p")), "column 0 keeps the layout-wide size");
        assertEquals(new Rect(0, -150, 220, 300), layout.band(0, 1, 1));
    }
}
