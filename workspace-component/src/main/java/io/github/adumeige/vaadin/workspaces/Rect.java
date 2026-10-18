package io.github.adumeige.vaadin.workspaces;

/**
 * An axis-aligned rectangle in workspace (canvas) coordinates.
 */
public record Rect(double x, double y, double width, double height) {

    public static final Rect EMPTY = new Rect(0, 0, 0, 0);

    public double centerX() { return x + width / 2; }
    public double centerY() { return y + height / 2; }
    public double right()   { return x + width; }
    public double bottom()  { return y + height; }

    public boolean contains(double px, double py) {
        return px >= x && px <= right() && py >= y && py <= bottom();
    }

    public Rect union(Rect other) {
        if (other == null) return this;
        double nx = Math.min(x, other.x);
        double ny = Math.min(y, other.y);
        double nr = Math.max(right(), other.right());
        double nb = Math.max(bottom(), other.bottom());
        return new Rect(nx, ny, nr - nx, nb - ny);
    }

    /** Squared distance from a point to the nearest edge of this rectangle, 0 if inside. */
    public double distanceSquaredTo(double px, double py) {
        double dx = Math.max(Math.max(x - px, 0), px - right());
        double dy = Math.max(Math.max(y - py, 0), py - bottom());
        return dx * dx + dy * dy;
    }

    public Rect translate(double dx, double dy) {
        return new Rect(x + dx, y + dy, width, height);
    }
}
