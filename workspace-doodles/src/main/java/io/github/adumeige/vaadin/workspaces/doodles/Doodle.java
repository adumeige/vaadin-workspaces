package io.github.adumeige.vaadin.workspaces.doodles;

import io.github.adumeige.vaadin.workspaces.Rect;

import java.util.List;
import java.util.UUID;

/** An annotation on the surface. Geometry is expressed through {@link Anchor}s. */
public sealed interface Doodle permits
        Doodle.Ellipse, Doodle.HighlightRect, Doodle.Arrow, Doodle.Stroke, Doodle.Marker,
        Doodle.TextHighlight, Doodle.Text, Doodle.Note {

    String id();
    DoodleStyle style();
    Doodle withStyle(DoodleStyle style);

    static String newId() { return UUID.randomUUID().toString().substring(0, 8); }

    record Ellipse(String id, DoodleStyle style, Anchor center, double rx, double ry) implements Doodle {
        public Ellipse withStyle(DoodleStyle s) { return new Ellipse(id, s, center, rx, ry); }
    }

    /** Translucent filled rectangle between two corners. */
    record HighlightRect(String id, DoodleStyle style, Anchor from, Anchor to) implements Doodle {
        public HighlightRect withStyle(DoodleStyle s) { return new HighlightRect(id, s, from, to); }
    }

    record Arrow(String id, DoodleStyle style, Anchor from, Anchor to) implements Doodle {
        public Arrow withStyle(DoodleStyle s) { return new Arrow(id, s, from, to); }
    }

    /** Freehand pen stroke, constant width under zoom. */
    record Stroke(String id, DoodleStyle style, List<Anchor> points) implements Doodle {
        public Stroke withStyle(DoodleStyle s) { return new Stroke(id, s, points); }
    }

    /** Wide translucent freehand stroke, width in workspace units. */
    record Marker(String id, DoodleStyle style, List<Anchor> points) implements Doodle {
        public Marker withStyle(DoodleStyle s) { return new Marker(id, s, points); }
    }

    /** Highlight over a text selection: rectangles relative to the item's top-left corner. */
    record TextHighlight(String id, DoodleStyle style, Object itemKey, List<Rect> rects) implements Doodle {
        public TextHighlight withStyle(DoodleStyle s) { return new TextHighlight(id, s, itemKey, rects); }
    }

    record Text(String id, DoodleStyle style, Anchor at, String text) implements Doodle {
        public Text withStyle(DoodleStyle s) { return new Text(id, s, at, text); }
    }

    /** A sticky note: a box of the given size with wrapping text. */
    record Note(String id, DoodleStyle style, Anchor at, double width, double height, String text) implements Doodle {
        public Note withStyle(DoodleStyle s) { return new Note(id, s, at, width, height, text); }
    }

    // ── Factories with fresh ids and type defaults ───────────────────────

    static Ellipse ellipse(Anchor center, double rx, double ry) { return new Ellipse(newId(), DoodleStyle.DEFAULT, center, rx, ry); }
    static HighlightRect highlight(Anchor from, Anchor to) { return new HighlightRect(newId(), DoodleStyle.HIGHLIGHT, from, to); }
    static Arrow arrow(Anchor from, Anchor to) { return new Arrow(newId(), DoodleStyle.DEFAULT, from, to); }
    static Stroke stroke(List<Anchor> points) { return new Stroke(newId(), DoodleStyle.DEFAULT, points); }
    static Marker marker(List<Anchor> points) { return new Marker(newId(), DoodleStyle.MARKER, points); }
    static TextHighlight textHighlight(Object itemKey, List<Rect> rects) { return new TextHighlight(newId(), DoodleStyle.HIGHLIGHT, itemKey, rects); }
    static Text text(Anchor at, String text) { return new Text(newId(), DoodleStyle.DEFAULT, at, text); }
    static Note note(Anchor at, double width, double height, String text) { return new Note(newId(), DoodleStyle.NOTE, at, width, height, text); }
}
