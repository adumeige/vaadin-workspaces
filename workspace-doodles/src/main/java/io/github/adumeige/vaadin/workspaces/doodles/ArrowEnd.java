package io.github.adumeige.vaadin.workspaces.doodles;

/** Decoration at either arrow anchor. Open shapes have transparent interiors. */
public enum ArrowEnd {
    NONE,
    /** The original arrowhead: filled in plain mode, open and hand-drawn in sketchy mode. */
    ARROW,
    DIAMOND_OPEN,
    DIAMOND_FILLED,
    CIRCLE_OPEN,
    CIRCLE_FILLED,
    TRIANGLE_OPEN,
    TRIANGLE_FILLED
}
