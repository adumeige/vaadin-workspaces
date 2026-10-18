package org.antoined.workspace.doodles.karibu

import com.github.mvysny.karibudsl.v10.VaadinDsl
import org.antoined.workspace.Placement
import org.antoined.workspace.Rect
import org.antoined.workspace.Workspace
import org.antoined.workspace.doodles.Anchor
import org.antoined.workspace.doodles.Doodle
import org.antoined.workspace.doodles.DoodleLayer
import org.antoined.workspace.doodles.DoodleStyle
import org.antoined.workspace.doodles.RenderMode

// ── Layer ────────────────────────────────────────────────────────────────

/** Attaches a doodle layer to the workspace being built: `gridWorkspace { doodleLayer { arrow(…) } }`. */
@VaadinDsl
fun <K> (@VaadinDsl Workspace<K>).doodleLayer(
    placement: Placement = Placement.ABOVE,
    block: (@VaadinDsl DoodleLayer<K>).() -> Unit = {},
): DoodleLayer<K> = DoodleLayer(this, placement).apply(block)

// ── Anchors ──────────────────────────────────────────────────────────────

fun at(x: Double, y: Double): Anchor = Anchor.at(x, y)

/** The point at fraction (fx, fy) of the item with the given key; defaults to its centre. */
fun on(key: Any, fx: Double = 0.5, fy: Double = 0.5): Anchor = Anchor.on(key, fx, fy)

// ── Style ────────────────────────────────────────────────────────────────

/** Mutable view of a [DoodleStyle] for the `style { }` block. */
class DoodleStyleBuilder(base: DoodleStyle) {
    var color: String = base.color()
    var strokeWidth: Double = base.strokeWidth()
    var opacity: Double = base.opacity()
    var dashed: Boolean = base.dashed()
    var render: RenderMode = base.render()
    fun build(): DoodleStyle = DoodleStyle(color, strokeWidth, opacity, dashed, render)
}

fun doodleStyle(base: DoodleStyle = DoodleStyle.DEFAULT, block: DoodleStyleBuilder.() -> Unit): DoodleStyle =
    DoodleStyleBuilder(base).apply(block).build()

/** A restyled copy, starting from the doodle's own style: `arrow(a, b).style { dashed = true }`. */
fun <D : Doodle> D.style(block: DoodleStyleBuilder.() -> Unit): D {
    @Suppress("UNCHECKED_CAST")
    return withStyle(doodleStyle(style(), block)) as D
}

// ── Doodles added to a layer ─────────────────────────────────────────────
// Each builder creates the doodle with its type's default style, lets the block restyle it,
// adds it to the layer and returns it (so its id is at hand for later restyle or remove).

@VaadinDsl
fun DoodleLayer<*>.ellipse(center: Anchor, rx: Double, ry: Double, block: DoodleStyleBuilder.() -> Unit = {}): Doodle.Ellipse =
    Doodle.ellipse(center, rx, ry).style(block).also { add(it) }

@VaadinDsl
fun DoodleLayer<*>.highlight(from: Anchor, to: Anchor, block: DoodleStyleBuilder.() -> Unit = {}): Doodle.HighlightRect =
    Doodle.highlight(from, to).style(block).also { add(it) }

@VaadinDsl
fun DoodleLayer<*>.arrow(from: Anchor, to: Anchor, block: DoodleStyleBuilder.() -> Unit = {}): Doodle.Arrow =
    Doodle.arrow(from, to).style(block).also { add(it) }

@VaadinDsl
fun DoodleLayer<*>.stroke(points: List<Anchor>, block: DoodleStyleBuilder.() -> Unit = {}): Doodle.Stroke =
    Doodle.stroke(points).style(block).also { add(it) }

@VaadinDsl
fun DoodleLayer<*>.marker(points: List<Anchor>, block: DoodleStyleBuilder.() -> Unit = {}): Doodle.Marker =
    Doodle.marker(points).style(block).also { add(it) }

@VaadinDsl
fun DoodleLayer<*>.textHighlight(itemKey: Any, rects: List<Rect>, block: DoodleStyleBuilder.() -> Unit = {}): Doodle.TextHighlight =
    Doodle.textHighlight(itemKey, rects).style(block).also { add(it) }

@VaadinDsl
fun DoodleLayer<*>.text(at: Anchor, text: String, block: DoodleStyleBuilder.() -> Unit = {}): Doodle.Text =
    Doodle.text(at, text).style(block).also { add(it) }

@VaadinDsl
fun DoodleLayer<*>.note(at: Anchor, width: Double, height: Double, text: String, block: DoodleStyleBuilder.() -> Unit = {}): Doodle.Note =
    Doodle.note(at, width, height, text).style(block).also { add(it) }
