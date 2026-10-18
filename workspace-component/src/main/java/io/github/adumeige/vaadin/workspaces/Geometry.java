package io.github.adumeige.vaadin.workspaces;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

/**
 * Spatial helpers a leaf may use when its navigation semantics happen to be geometric.
 * Nothing in the base layers depends on this class.
 */
public final class Geometry {

    private Geometry() {}

    /** The key whose rectangle is nearest to the point (containing rectangles win with distance 0). */
    public static <K> Optional<K> nearest(double px, double py, Collection<K> keys, Function<K, Rect> bounds) {
        K best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (K key : keys) {
            double d = bounds.apply(key).distanceSquaredTo(px, py);
            if (d < bestD) {
                bestD = d;
                best = key;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * The best candidate in a direction from a starting key. A candidate qualifies when its
     * centre lies strictly inside the 90-degree cone opening in the direction. Among candidates, the one with
     * the smallest weighted distance wins: distance along the axis counts once, lateral
     * distance counts twice, so items roughly aligned with the start are preferred.
     */
    public static <K> Optional<K> neighbour(K from, Direction dir, Collection<K> keys, Function<K, Rect> bounds) {
        Rect origin = bounds.apply(from);
        double ox = origin.centerX(), oy = origin.centerY();
        K best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (K key : keys) {
            if (key.equals(from)) continue;
            Rect r = bounds.apply(key);
            double vx = r.centerX() - ox;
            double vy = r.centerY() - oy;
            double along = vx * dir.dx + vy * dir.dy;
            double lateral = Math.abs(vx * dir.dy) + Math.abs(vy * dir.dx);
            if (along <= 0 || lateral >= along) continue;
            double score = along + 2 * lateral;
            if (score < bestScore) {
                bestScore = score;
                best = key;
            }
        }
        return Optional.ofNullable(best);
    }
}
