package org.antoined.workspace.doodles;

import elemental.json.Json;
import elemental.json.JsonArray;
import elemental.json.JsonObject;
import org.antoined.workspace.Point;
import org.antoined.workspace.Rect;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Converts doodles to the client's flat JSON (anchors resolved to absolute numbers) and parses
 * client-drawn shapes back into doodles, anchoring them to items when asked.
 * Pure; the environment is abstracted by {@link Resolver} so this is unit-testable.
 */
public final class DoodleJson {

    /** What the codec needs to know about the workspace. */
    public interface Resolver {
        Optional<Point> resolve(Object key, double fx, double fy);
        Optional<Rect> bounds(Object key);
        Optional<Integer> itemId(Object key);
        Optional<Object> key(int itemId);
    }

    private final Resolver resolver;
    private RenderMode defaultRender = RenderMode.PLAIN;

    public DoodleJson(Resolver resolver) {
        this.resolver = resolver;
    }

    /** Render mode substituted for {@link RenderMode#DEFAULT} when serialising. */
    public void setDefaultRender(RenderMode mode) {
        this.defaultRender = mode == null || mode == RenderMode.DEFAULT ? RenderMode.PLAIN : mode;
    }

    // ── To client ────────────────────────────────────────────────────────

    public JsonArray toJson(List<Doodle> doodles) {
        JsonArray arr = Json.createArray();
        for (Doodle d : doodles) {
            JsonObject o = toJson(d);
            if (o != null) arr.set(arr.length(), o);
        }
        return arr;
    }

    /** Null when an anchor cannot be resolved (its item is gone). */
    public JsonObject toJson(Doodle d) {
        JsonObject o = Json.createObject();
        o.put("id", d.id());
        o.put("style", style(d.style(), defaultRender));
        try {
            if (d instanceof Doodle.Ellipse e) {
                o.put("type", "ellipse");
                Point c = point(e.center());
                o.put("cx", c.x()); o.put("cy", c.y()); o.put("rx", e.rx()); o.put("ry", e.ry());
            } else if (d instanceof Doodle.HighlightRect r) {
                o.put("type", "rect");
                corners(o, point(r.from()), point(r.to()));
            } else if (d instanceof Doodle.Arrow a) {
                o.put("type", "arrow");
                Point f = point(a.from()), t = point(a.to());
                o.put("x1", f.x()); o.put("y1", f.y()); o.put("x2", t.x()); o.put("y2", t.y());
            } else if (d instanceof Doodle.Stroke s) {
                o.put("type", "stroke"); o.put("points", points(s.points()));
            } else if (d instanceof Doodle.Marker m) {
                o.put("type", "marker"); o.put("points", points(m.points()));
            } else if (d instanceof Doodle.TextHighlight th) {
                o.put("type", "textHighlight");
                Rect item = resolver.bounds(th.itemKey()).orElseThrow(Unresolved::new);
                JsonArray rects = Json.createArray();
                for (Rect r : th.rects()) {
                    JsonObject ro = Json.createObject();
                    ro.put("x", item.x() + r.x()); ro.put("y", item.y() + r.y());
                    ro.put("w", r.width()); ro.put("h", r.height());
                    rects.set(rects.length(), ro);
                }
                o.put("rects", rects);
            } else if (d instanceof Doodle.Text t) {
                o.put("type", "text");
                Point p = point(t.at());
                o.put("x", p.x()); o.put("y", p.y()); o.put("text", t.text());
            } else if (d instanceof Doodle.Note n) {
                o.put("type", "note");
                Point p = point(n.at());
                o.put("x", p.x()); o.put("y", p.y()); o.put("w", n.width()); o.put("h", n.height()); o.put("text", n.text());
            }
        } catch (Unresolved u) {
            return null;
        }
        return o;
    }

    private static final class Unresolved extends RuntimeException {}

    private Point point(Anchor a) {
        if (a instanceof Anchor.Absolute abs) return new Point(abs.x(), abs.y());
        Anchor.OnItem on = (Anchor.OnItem) a;
        return resolver.resolve(on.key(), on.fx(), on.fy()).orElseThrow(Unresolved::new);
    }

    private JsonArray points(List<Anchor> anchors) {
        JsonArray arr = Json.createArray();
        for (Anchor a : anchors) {
            Point p = point(a);
            JsonObject po = Json.createObject();
            po.put("x", p.x()); po.put("y", p.y());
            arr.set(arr.length(), po);
        }
        return arr;
    }

    private static void corners(JsonObject o, Point a, Point b) {
        o.put("x", Math.min(a.x(), b.x())); o.put("y", Math.min(a.y(), b.y()));
        o.put("w", Math.abs(b.x() - a.x())); o.put("h", Math.abs(b.y() - a.y()));
    }

    public static JsonObject style(DoodleStyle s) {
        return style(s, RenderMode.DEFAULT);
    }

    /** Serialise a style; a {@code DEFAULT} render mode is replaced by {@code fallback}. */
    public static JsonObject style(DoodleStyle s, RenderMode fallback) {
        JsonObject o = Json.createObject();
        o.put("color", s.color()); o.put("width", s.strokeWidth()); o.put("opacity", s.opacity()); o.put("dashed", s.dashed());
        RenderMode r = s.render() == null || s.render() == RenderMode.DEFAULT ? fallback : s.render();
        o.put("render", r.name());
        return o;
    }

    public static DoodleStyle style(JsonObject o) {
        RenderMode render = RenderMode.DEFAULT;
        if (o.hasKey("render")) {
            try { render = RenderMode.valueOf(o.getString("render")); } catch (IllegalArgumentException ignored) { }
        }
        return new DoodleStyle(
                o.hasKey("color") ? o.getString("color") : DoodleStyle.DEFAULT.color(),
                o.hasKey("width") ? o.getNumber("width") : DoodleStyle.DEFAULT.strokeWidth(),
                o.hasKey("opacity") ? o.getNumber("opacity") : 1.0,
                o.hasKey("dashed") && o.getBoolean("dashed"),
                render);
    }

    // ── From client ──────────────────────────────────────────────────────

    /**
     * Parse a client-drawn shape. Points carry absolute coordinates and optionally the id of the
     * item under them; with {@code anchorToItems} such points become {@link Anchor.OnItem}.
     */
    public Optional<Doodle> fromClient(JsonObject o, boolean anchorToItems) {
        String type = o.getString("type");
        String id = o.hasKey("id") ? o.getString("id") : Doodle.newId();
        DoodleStyle style = o.hasKey("style") ? style(o.getObject("style")) : DoodleStyle.DEFAULT;
        return Optional.ofNullable(switch (type) {
            case "ellipse" -> new Doodle.Ellipse(id, style, anchor(o.getObject("center"), anchorToItems), o.getNumber("rx"), o.getNumber("ry"));
            case "rect" -> new Doodle.HighlightRect(id, style, anchor(o.getObject("from"), anchorToItems), anchor(o.getObject("to"), anchorToItems));
            case "arrow" -> new Doodle.Arrow(id, style, anchor(o.getObject("from"), anchorToItems), anchor(o.getObject("to"), anchorToItems));
            case "stroke" -> new Doodle.Stroke(id, style, anchors(o.getArray("points"), anchorToItems));
            case "marker" -> new Doodle.Marker(id, style, anchors(o.getArray("points"), anchorToItems));
            case "textHighlight" -> textHighlight(id, style, o);
            case "text" -> new Doodle.Text(id, style, anchor(o.getObject("at"), anchorToItems), o.getString("text"));
            case "note" -> new Doodle.Note(id, style, anchor(o.getObject("at"), anchorToItems), o.getNumber("w"), o.getNumber("h"), o.getString("text"));
            default -> null;
        });
    }

    private Doodle textHighlight(String id, DoodleStyle style, JsonObject o) {
        Object key = resolver.key((int) o.getNumber("itemId")).orElse(null);
        if (key == null) return null;
        List<Rect> rects = new ArrayList<>();
        JsonArray arr = o.getArray("rects");
        for (int i = 0; i < arr.length(); i++) {
            JsonObject r = arr.getObject(i);
            rects.add(new Rect(r.getNumber("x"), r.getNumber("y"), r.getNumber("w"), r.getNumber("h")));
        }
        return new Doodle.TextHighlight(id, style, key, rects);
    }

    private List<Anchor> anchors(JsonArray arr, boolean anchorToItems) {
        List<Anchor> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) list.add(anchor(arr.getObject(i), anchorToItems));
        return list;
    }

    private Anchor anchor(JsonObject p, boolean anchorToItems) {
        double x = p.getNumber("x"), y = p.getNumber("y");
        if (anchorToItems && p.hasKey("itemId") && p.get("itemId").getType() == elemental.json.JsonType.NUMBER) {
            Optional<Object> key = resolver.key((int) p.getNumber("itemId"));
            if (key.isPresent()) {
                Optional<Rect> b = resolver.bounds(key.get());
                if (b.isPresent() && b.get().width() > 0 && b.get().height() > 0) {
                    Rect r = b.get();
                    return new Anchor.OnItem(key.get(), (x - r.x()) / r.width(), (y - r.y()) / r.height());
                }
            }
        }
        return new Anchor.Absolute(x, y);
    }

}
