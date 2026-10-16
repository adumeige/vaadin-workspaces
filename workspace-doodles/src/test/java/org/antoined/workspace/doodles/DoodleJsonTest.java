package org.antoined.workspace.doodles;

import elemental.json.Json;
import elemental.json.JsonArray;
import elemental.json.JsonObject;
import org.antoined.workspace.Point;
import org.antoined.workspace.Rect;
import org.antoined.workspace.layout.GridCell;
import org.antoined.workspace.layout.GridLayout;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DoodleJsonTest {

    /** Two grid cells, ids 1 and 2, on a mutable grid layout. */
    private final GridLayout grid = new GridLayout(100, 50, 0);
    private final Map<Integer, GridCell> byId = Map.of(1, new GridCell(0, 0), 2, new GridCell(1, 0));
    private final DoodleJson.Resolver resolver = new DoodleJson.Resolver() {
        public Optional<Point> resolve(Object key, double fx, double fy) {
            return bounds(key).map(r -> new Point(r.x() + r.width() * fx, r.y() + r.height() * fy));
        }
        public Optional<Rect> bounds(Object key) {
            return byId.containsValue(key) ? Optional.of(grid.bounds((GridCell) key)) : Optional.empty();
        }
        public Optional<Integer> itemId(Object key) {
            return byId.entrySet().stream().filter(e -> e.getValue().equals(key)).map(Map.Entry::getKey).findFirst();
        }
        public Optional<Object> key(int itemId) { return Optional.ofNullable(byId.get(itemId)); }
    };
    private final DoodleJson json = new DoodleJson(resolver);

    @Test
    void itemAnchoredArrowResolvesAndFollowsRelayout() {
        Doodle.Arrow arrow = Doodle.arrow(Anchor.on(new GridCell(0, 0), 1, 0.5), Anchor.on(new GridCell(1, 0), 0, 0.5));
        JsonObject o = json.toJson(arrow);
        assertEquals("arrow", o.getString("type"));
        assertEquals(100, o.getNumber("x1")); assertEquals(25, o.getNumber("y1"));
        assertEquals(100, o.getNumber("x2"));
        grid.setMargin(20);
        o = json.toJson(arrow);
        assertEquals(120, o.getNumber("x2"), "the end follows the cell after relayout");
    }

    @Test
    void unresolvableAnchorDropsTheDoodle() {
        Doodle d = Doodle.ellipse(Anchor.on(new GridCell(9, 9), 0.5, 0.5), 10, 10);
        assertNull(json.toJson(d));
        assertEquals(0, json.toJson(List.of(d)).length());
    }

    @Test
    void everyTypeSerialises() {
        List<Doodle> all = List.of(
                Doodle.ellipse(Anchor.at(1, 2), 3, 4),
                Doodle.highlight(Anchor.at(10, 10), Anchor.at(0, 0)),
                Doodle.arrow(Anchor.at(0, 0), Anchor.at(5, 5)),
                Doodle.stroke(List.of(Anchor.at(0, 0), Anchor.at(1, 1))),
                Doodle.marker(List.of(Anchor.at(0, 0), Anchor.at(1, 1))),
                Doodle.textHighlight(new GridCell(1, 0), List.of(new Rect(5, 5, 20, 10))),
                Doodle.text(Anchor.at(3, 3), "hi"),
                Doodle.note(Anchor.at(3, 3), 100, 60, "note"));
        JsonArray arr = json.toJson(all);
        assertEquals(8, arr.length());
        JsonObject rect = arr.getObject(1);
        assertEquals(0, rect.getNumber("x")); assertEquals(10, rect.getNumber("w"));
        JsonObject th = arr.getObject(5);
        assertEquals(105, th.getArray("rects").getObject(0).getNumber("x"), "relative to the item's corner");
        assertEquals(0.35, th.getObject("style").getNumber("opacity"));
    }

    @Test
    void clientPointsOnItemsBecomeItemAnchors() {
        JsonObject o = Json.createObject();
        o.put("type", "arrow");
        o.put("from", point(50, 25, 1));
        o.put("to", point(150, 25, 2));
        Doodle d = json.fromClient(o, true).orElseThrow();
        Doodle.Arrow a = assertInstanceOf(Doodle.Arrow.class, d);
        Anchor.OnItem from = assertInstanceOf(Anchor.OnItem.class, a.from());
        assertEquals(new GridCell(0, 0), from.key());
        assertEquals(0.5, from.fx(), 1e-9);
        assertEquals(0.5, from.fy(), 1e-9);
        Anchor.OnItem to = assertInstanceOf(Anchor.OnItem.class, a.to());
        assertEquals(new GridCell(1, 0), to.key());

        Doodle plain = json.fromClient(o, false).orElseThrow();
        assertInstanceOf(Anchor.Absolute.class, ((Doodle.Arrow) plain).from());
    }

    @Test
    void clientPointsOffItemsStayAbsoluteAndUnknownIdsAreIgnored() {
        JsonObject o = Json.createObject();
        o.put("type", "stroke");
        JsonArray pts = Json.createArray();
        pts.set(0, point(500, 500, null));
        pts.set(1, point(510, 510, 99));
        o.put("points", pts);
        Doodle.Stroke s = (Doodle.Stroke) json.fromClient(o, true).orElseThrow();
        assertTrue(s.points().stream().allMatch(a -> a instanceof Anchor.Absolute));
    }

    @Test
    void clientTextHighlightNeedsAKnownItem() {
        JsonObject o = Json.createObject();
        o.put("type", "textHighlight");
        o.put("itemId", 2);
        JsonArray rects = Json.createArray();
        JsonObject r = Json.createObject(); r.put("x", 1); r.put("y", 2); r.put("w", 3); r.put("h", 4);
        rects.set(0, r);
        o.put("rects", rects);
        Doodle.TextHighlight th = (Doodle.TextHighlight) json.fromClient(o, true).orElseThrow();
        assertEquals(new GridCell(1, 0), th.itemKey());
        o.put("itemId", 42);
        assertTrue(json.fromClient(o, true).isEmpty());
    }

    @Test
    void renderModeFallsBackToLayerDefaultAndPerDoodleOverrideWins() {
        Doodle plain = Doodle.ellipse(Anchor.at(0, 0), 1, 1);
        Doodle sketchy = plain.withStyle(plain.style().withRender(RenderMode.SKETCHY));
        assertEquals("PLAIN", json.toJson(plain).getObject("style").getString("render"));
        json.setDefaultRender(RenderMode.SKETCHY);
        assertEquals("SKETCHY", json.toJson(plain).getObject("style").getString("render"));
        json.setDefaultRender(RenderMode.PLAIN);
        assertEquals("SKETCHY", json.toJson(sketchy).getObject("style").getString("render"));
        JsonObject st = Json.createObject(); st.put("render", "SKETCHY");
        assertEquals(RenderMode.SKETCHY, DoodleJson.style(st).render());
    }

    @Test
    void unknownTypeIsIgnored() {
        JsonObject o = Json.createObject();
        o.put("type", "banana");
        assertTrue(json.fromClient(o, true).isEmpty());
    }

    private static JsonObject point(double x, double y, Integer itemId) {
        JsonObject p = Json.createObject();
        p.put("x", x); p.put("y", y);
        if (itemId != null) p.put("itemId", itemId);
        return p;
    }
}
