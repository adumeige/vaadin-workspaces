package io.github.adumeige.vaadin.workspaces;

/**
 * The viewport state: which workspace point is at the viewport centre, and at what scale.
 */
public record ViewState(double centerX, double centerY, double scale) {
}
