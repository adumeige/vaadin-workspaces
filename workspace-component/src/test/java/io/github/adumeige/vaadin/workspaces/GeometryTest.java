package io.github.adumeige.vaadin.workspaces;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeometryTest {

    private final Map<String, Rect> rects = Map.of(
            "a", new Rect(0, 0, 100, 100),
            "b", new Rect(200, 0, 100, 100),
            "c", new Rect(0, 200, 100, 100),
            "d", new Rect(400, 20, 100, 100));
    private final List<String> keys = List.of("a", "b", "c", "d");

    @Test
    void nearestPrefersContainingRect() {
        assertEquals(Optional.of("b"), Geometry.nearest(250, 50, keys, rects::get));
    }

    @Test
    void nearestPicksClosestEdgeWhenOutside() {
        assertEquals(Optional.of("c"), Geometry.nearest(50, 180, keys, rects::get));
    }

    @Test
    void neighbourRightPicksNearestInCone() {
        assertEquals(Optional.of("b"), Geometry.neighbour("a", Direction.RIGHT, keys, rects::get));
        assertEquals(Optional.of("d"), Geometry.neighbour("b", Direction.RIGHT, keys, rects::get));
    }

    @Test
    void neighbourDownIgnoresItemsOutsideCone() {
        assertEquals(Optional.of("c"), Geometry.neighbour("a", Direction.DOWN, keys, rects::get));
        assertTrue(Geometry.neighbour("b", Direction.DOWN, keys, rects::get).isEmpty());
    }

    @Test
    void neighbourStopsAtEdge() {
        assertTrue(Geometry.neighbour("a", Direction.LEFT, keys, rects::get).isEmpty());
        assertTrue(Geometry.neighbour("d", Direction.RIGHT, keys, rects::get).isEmpty());
    }

    @Test
    void rectUnionAndDistance() {
        Rect u = rects.get("a").union(rects.get("d"));
        assertEquals(new Rect(0, 0, 500, 120), u);
        assertEquals(0, rects.get("a").distanceSquaredTo(50, 50));
        assertEquals(100 * 100, rects.get("a").distanceSquaredTo(200, 50));
    }
}
