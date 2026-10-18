package io.github.adumeige.vaadin.workspaces.layout;

import io.github.adumeige.vaadin.workspaces.Rect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LayoutsTest {

    @Test
    void gridBoundsIncludeMargin() {
        GridLayout g = new GridLayout(800, 600, 12);
        assertEquals(new Rect(0, 0, 800, 600), g.bounds(new GridCell(0, 0)));
        assertEquals(new Rect(812 * 2, 612, 800, 600), g.bounds(new GridCell(2, 1)));
    }

    @Test
    void gridInverseMappingIsExactAtCentresAndRobustOffCentre() {
        GridLayout g = new GridLayout(800, 600, 12);
        Rect r = g.bounds(new GridCell(2, 1));
        assertEquals(new GridCell(2, 1), g.cellAt(r.centerX(), r.centerY()));
        assertEquals(new GridCell(2, 1), g.cellAt(r.centerX() + 300, r.centerY() - 200));
        assertEquals(new GridCell(-1, 0), g.cellAt(-500, 100));
    }

    @Test
    void gridExtentIsUnion() {
        GridLayout g = new GridLayout(100, 100, 10);
        Rect e = g.extent(List.of(new GridCell(0, 0), new GridCell(2, 1)));
        assertEquals(new Rect(0, 0, 320, 210), e);
    }

    @Test
    void linearHorizontalAccumulatesVariableLengths() {
        LinearLayout l = new LinearLayout(LinearLayout.Orientation.HORIZONTAL, i -> 100 + i * 50, 80, 10);
        assertEquals(new Rect(0, 0, 100, 80), l.bounds(0));
        assertEquals(new Rect(110, 0, 150, 80), l.bounds(1));
        assertEquals(new Rect(270, 0, 200, 80), l.bounds(2));
    }

    @Test
    void linearVerticalSwapsAxes() {
        LinearLayout l = new LinearLayout(LinearLayout.Orientation.VERTICAL, 100, 80, 10);
        assertEquals(new Rect(0, 220, 80, 100), l.bounds(2));
    }

    @Test
    void freeLayoutReturnsPlacedRects() {
        FreeLayout<String> f = new FreeLayout<String>().place("x", 1, 2, 3, 4);
        assertEquals(new Rect(1, 2, 3, 4), f.bounds("x"));
    }
}
