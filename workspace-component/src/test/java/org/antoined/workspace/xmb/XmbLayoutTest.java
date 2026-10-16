package org.antoined.workspace.xmb;

import org.antoined.workspace.Rect;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmbLayoutTest {

    private final XmbNavigator<String, Integer> nav = new XmbNavigator<>();
    private final XmbLayout<String, Integer> layout = new XmbLayout<>(nav)
            .categorySize(100, 50).categoryGap(20).itemSize(80, 30).itemGap(10).headerGap(5);

    XmbLayoutTest() {
        nav.setModel(List.of("a", "b"), c -> Map.of("a", List.of(0, 1, 2), "b", List.of(0)).get(c), i -> true);
    }

    @Test
    void categoriesFormARow() {
        assertEquals(new Rect(0, 0, 100, 50), layout.bounds(new XmbKey.Category<>("a")));
        assertEquals(new Rect(120, 0, 100, 50), layout.bounds(new XmbKey.Category<>("b")));
    }

    @Test
    void focusedItemSitsUnderTheRowAndColumnShiftsWithFocus() {
        assertEquals(new Rect(10, 55, 80, 30), layout.bounds(new XmbKey.Item<>("a", 0)));
        assertEquals(new Rect(10, 95, 80, 30), layout.bounds(new XmbKey.Item<>("a", 1)));
        nav.execute(NavigationCommand.NEXT_ITEM);
        assertEquals(new Rect(10, 55, 80, 30), layout.bounds(new XmbKey.Item<>("a", 1)));
        Rect above = layout.bounds(new XmbKey.Item<>("a", 0));
        assertTrue(above.bottom() < 0, "items before the focused one sit above the category row");
        assertEquals(new Rect(10, -45, 80, 30), above);
    }

    @Test
    void itemColumnCanAlignWithHeaderEdges() {
        layout.itemAlignment(XmbLayout.ItemAlignment.START);
        assertEquals(new Rect(0, 55, 80, 30), layout.bounds(new XmbKey.Item<>("a", 0)));
        layout.itemAlignment(XmbLayout.ItemAlignment.END);
        assertEquals(new Rect(20, 55, 80, 30), layout.bounds(new XmbKey.Item<>("a", 0)));
    }

    @Test
    void inactiveCategoryItemsCollapseBehindHeader() {
        assertEquals(new Rect(130, 55, 80, 30), layout.bounds(new XmbKey.Item<>("b", 0)));
    }
}
