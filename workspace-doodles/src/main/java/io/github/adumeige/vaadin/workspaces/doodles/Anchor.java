package io.github.adumeige.vaadin.workspaces.doodles;

/** Where a doodle point lives: fixed on the surface, or at a fraction of an item's bounds. */
public sealed interface Anchor permits Anchor.Absolute, Anchor.OnItem {

    record Absolute(double x, double y) implements Anchor {}

    /** Fraction (fx, fy) of the item's bounds, 0..1 on each axis; follows the item on relayout. */
    record OnItem(Object key, double fx, double fy) implements Anchor {}

    static Anchor at(double x, double y) { return new Absolute(x, y); }

    static Anchor on(Object key, double fx, double fy) { return new OnItem(key, fx, fy); }
}
