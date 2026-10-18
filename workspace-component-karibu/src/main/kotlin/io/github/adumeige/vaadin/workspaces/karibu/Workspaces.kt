package io.github.adumeige.vaadin.workspaces.karibu

import com.github.mvysny.karibudsl.v10.VaadinDsl
import com.github.mvysny.karibudsl.v10.buildSingleComponent
import com.github.mvysny.karibudsl.v10.init
import com.vaadin.flow.component.Component
import com.vaadin.flow.component.HasComponents
import io.github.adumeige.vaadin.workspaces.FreeWorkspace
import io.github.adumeige.vaadin.workspaces.GridWorkspace
import io.github.adumeige.vaadin.workspaces.StripWorkspace
import io.github.adumeige.vaadin.workspaces.Workspace
import io.github.adumeige.vaadin.workspaces.WorkspaceLayout
import io.github.adumeige.vaadin.workspaces.cascade.CascadeWorkspace
import io.github.adumeige.vaadin.workspaces.cascade.SlidingColumn
import io.github.adumeige.vaadin.workspaces.layout.LinearLayout
import io.github.adumeige.vaadin.workspaces.xmb.XMediaBar

// ── Workspaces as children of any layout ─────────────────────────────────

/** A bare [Workspace] over the given layout, for custom leaves built in Kotlin. */
@VaadinDsl
fun <K> (@VaadinDsl HasComponents).workspace(
    layout: WorkspaceLayout<K>,
    block: (@VaadinDsl Workspace<K>).() -> Unit = {},
): Workspace<K> = init(Workspace(layout), block)

@VaadinDsl
fun (@VaadinDsl HasComponents).gridWorkspace(
    cellWidth: Int = 800,
    cellHeight: Int = 600,
    block: (@VaadinDsl GridWorkspace).() -> Unit = {},
): GridWorkspace = init(GridWorkspace(cellWidth, cellHeight), block)

@VaadinDsl
fun (@VaadinDsl HasComponents).stripWorkspace(
    orientation: LinearLayout.Orientation = LinearLayout.Orientation.HORIZONTAL,
    itemLength: Double,
    crossSize: Double,
    gap: Double = 0.0,
    block: (@VaadinDsl StripWorkspace).() -> Unit = {},
): StripWorkspace = init(StripWorkspace(orientation, itemLength, crossSize, gap), block)

@VaadinDsl
fun <K> (@VaadinDsl HasComponents).freeWorkspace(
    block: (@VaadinDsl FreeWorkspace<K>).() -> Unit = {},
): FreeWorkspace<K> = init(FreeWorkspace(), block)

@VaadinDsl
fun <C, I> (@VaadinDsl HasComponents).xMediaBar(
    block: (@VaadinDsl XMediaBar<C, I>).() -> Unit = {},
): XMediaBar<C, I> = init(XMediaBar(), block)

@VaadinDsl
fun (@VaadinDsl HasComponents).cascadeWorkspace(
    block: (@VaadinDsl CascadeWorkspace).() -> Unit = {},
): CascadeWorkspace = init(CascadeWorkspace(), block)

// ── Content builders ─────────────────────────────────────────────────────
// Each block must add exactly one component (karibu's buildSingleComponent enforces it); that
// component becomes the item, so renderers read `{ c -> div { h4(c.name) } }`.

/** A grid cell whose content is built inline: `cell(0, 0) { card { … } }`. */
@VaadinDsl
fun (@VaadinDsl GridWorkspace).cell(col: Int, row: Int, block: (@VaadinDsl HasComponents).() -> Unit): Component =
    buildSingleComponent("workspace item", block).also { addCell(col, row, it) }

/** Appends an item to the strip, with the layout's default length unless given. */
@VaadinDsl
fun (@VaadinDsl StripWorkspace).item(length: Double? = null, block: (@VaadinDsl HasComponents).() -> Unit): Component =
    buildSingleComponent("workspace item", block).also { if (length == null) append(it) else append(it, length) }

/** Places an item on a free workspace with inline content. */
@VaadinDsl
fun <K> (@VaadinDsl FreeWorkspace<K>).place(
    key: K, x: Double, y: Double, width: Double, height: Double,
    block: (@VaadinDsl HasComponents).() -> Unit,
): Component = buildSingleComponent("workspace item", block).also { place(key, x, y, width, height, it) }

// ── Renderers as DSL blocks ──────────────────────────────────────────────

@VaadinDsl
fun <C, I> (@VaadinDsl XMediaBar<C, I>).categoryRenderer(block: (@VaadinDsl HasComponents).(C) -> Unit) {
    setCategoryRenderer { c -> buildSingleComponent("category renderer") { block(c) } }
}

@VaadinDsl
fun <C, I> (@VaadinDsl XMediaBar<C, I>).itemRenderer(block: (@VaadinDsl HasComponents).(C, I) -> Unit) {
    setItemRenderer { c, i -> buildSingleComponent("item renderer") { block(c, i) } }
}

/** Adds a cascade column whose items are rendered by the block: `val cases = column<Case> { c -> div { … } }`. */
@VaadinDsl
fun <T> (@VaadinDsl CascadeWorkspace).column(block: (@VaadinDsl HasComponents).(T) -> Unit): SlidingColumn<T> =
    addColumn { t -> buildSingleComponent("column renderer") { block(t) } }

@VaadinDsl
fun <T> (@VaadinDsl CascadeWorkspace).renderer(column: SlidingColumn<T>, block: (@VaadinDsl HasComponents).(T) -> Unit) {
    setRenderer(column) { t -> buildSingleComponent("column renderer") { block(t) } }
}
