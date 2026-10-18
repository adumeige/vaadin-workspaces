package org.antoined.workspace.doodles;

/**
 * Visual style. {@code strokeWidth} is in CSS pixels for outlines (constant under zoom) and in
 * workspace units for markers (they must cover the same content at any zoom).
 */
public record DoodleStyle(String color, double strokeWidth, double opacity, boolean dashed, RenderMode render) {

    public DoodleStyle(String color, double strokeWidth, double opacity, boolean dashed) {
        this(color, strokeWidth, opacity, dashed, RenderMode.DEFAULT);
    }

    public static final DoodleStyle DEFAULT = new DoodleStyle("#e53e3e", 3, 1.0, false);
    public static final DoodleStyle HIGHLIGHT = new DoodleStyle("#f6e05e", 0, 0.35, false);
    public static final DoodleStyle MARKER = new DoodleStyle("#f6e05e", 18, 0.45, false);
    public static final DoodleStyle NOTE = new DoodleStyle("#fefcbf", 1, 1.0, false);

    public DoodleStyle withColor(String color) { return new DoodleStyle(color, strokeWidth, opacity, dashed, render); }
    public DoodleStyle withStrokeWidth(double w) { return new DoodleStyle(color, w, opacity, dashed, render); }
    public DoodleStyle withOpacity(double o) { return new DoodleStyle(color, strokeWidth, o, dashed, render); }
    public DoodleStyle withDashed(boolean d) { return new DoodleStyle(color, strokeWidth, opacity, d, render); }
    /** Per-doodle render mode; {@link RenderMode#DEFAULT} follows the layer. */
    public DoodleStyle withRender(RenderMode r) { return new DoodleStyle(color, strokeWidth, opacity, dashed, r); }
}
