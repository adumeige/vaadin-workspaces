package org.antoined.demo

import com.github.mvysny.karibudsl.v10.KComposite
import com.github.mvysny.karibudsl.v10.button
import com.github.mvysny.karibudsl.v10.checkBox
import com.github.mvysny.karibudsl.v10.css
import com.github.mvysny.karibudsl.v10.horizontalLayout
import com.github.mvysny.karibudsl.v10.onClick
import com.github.mvysny.karibudsl.v10.p
import com.github.mvysny.karibudsl.v10.select
import com.github.mvysny.karibudsl.v10.verticalLayout
import com.github.mvysny.karibudsl.v23.card
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.router.Route
import kotlinx.css.background
import org.antoined.workspace.GridWorkspace
import org.antoined.workspace.doodles.DoodleLayer
import org.antoined.workspace.doodles.DoodleTool
import org.antoined.workspace.doodles.RenderMode
import org.antoined.workspace.doodles.karibu.arrow
import org.antoined.workspace.doodles.karibu.at
import org.antoined.workspace.doodles.karibu.doodleLayer
import org.antoined.workspace.doodles.karibu.ellipse
import org.antoined.workspace.doodles.karibu.note
import org.antoined.workspace.doodles.karibu.on
import org.antoined.workspace.karibu.cell
import org.antoined.workspace.karibu.gridWorkspace
import org.antoined.workspace.layout.GridCell

private const val LOREM = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua."

private val tools = listOf(
    DoodleTool.NONE to "Select", DoodleTool.ELLIPSE to "Circle", DoodleTool.RECT to "Highlight",
    DoodleTool.ARROW to "Arrow", DoodleTool.PEN to "Pen", DoodleTool.MARKER to "Marker",
    DoodleTool.TEXT_MARKER to "Text marker", DoodleTool.TEXT to "Text", DoodleTool.NOTE to "Note",
    DoodleTool.ERASER to "Eraser",
)

@Route("doodles")
class DoodlesView : KComposite() {

    private lateinit var grid: GridWorkspace
    private lateinit var doodles: DoodleLayer<GridCell>
    private val toolButtons = mutableMapOf<DoodleTool, Button>()
    private var margin = 40

    private fun refreshTools() = toolButtons.forEach { (t, b) ->
        if (t == doodles.tool) b.addThemeVariants(ButtonVariant.LUMO_PRIMARY) else b.removeThemeVariants(ButtonVariant.LUMO_PRIMARY)
    }

    private val root = ui {
        verticalLayout(padding = false, spacing = false) {
            setSizeFull()
            grid = gridWorkspace(600, 400) {
                setSize("100%", "100%")
                setCellMargin(margin)
                css { background = "#f0f0f0" }
                for (col in 0..2) for (row in 0..1) {
                    cell(col, row) {
                        card {
                            header = H3("Cell ($col,$row)")
                            setSizeFull()
                            p(LOREM)
                            p("Select some of this text with the text-marker tool to highlight it.")
                        }
                    }
                }
                doodles = doodleLayer {
                    addDoodleAddedListener { println("Doodle added (${if (it.isFromClient) "client" else "server"}): ${it.doodle}") }
                    addDoodleRemovedListener { println("Doodle removed: ${it.doodle.id()}") }
                    // Select mode: clicking a doodle toggles its own render mode (per-doodle override).
                    addDoodleClickedListener { e ->
                        val current = e.doodle.style().render()
                        val effective = if (current == RenderMode.DEFAULT) renderMode else current
                        val next = if (effective == RenderMode.SKETCHY) RenderMode.PLAIN else RenderMode.SKETCHY
                        restyle(e.doodle.id(), e.doodle.style().withRender(next))
                        Notification.show("Doodle ${e.doodle.id()} now ${next.name.lowercase()}")
                    }
                    // Programmatic doodles: an item-anchored arrow, an absolute ellipse and a note.
                    arrow(on(GridCell(0, 0), 1.0, 0.5), on(GridCell(1, 0), 0.0, 0.5))
                    ellipse(at(300.0, 200.0), 200.0, 120.0) { color = "#3182ce"; dashed = true }
                    note(on(GridCell(2, 1), 0.6, 0.1), 220.0, 110.0, "A sticky note anchored to cell (2,1). Drag the margin slider to see it follow.")
                    addToolChangedListener { refreshTools() }
                }
                setInitialFitAll()
            }
            horizontalLayout(padding = true) {
                isWrap = true
                tools.forEach { (tool, label) ->
                    toolButtons[tool] = button(label) { onClick { doodles.tool = tool; refreshTools() } }
                }
                select<String> {
                    setItems("#e53e3e", "#3182ce", "#38a169", "#d69e2e", "#805ad5", "#f6e05e")
                    value = "#e53e3e"
                    addValueChangeListener { doodles.setDrawStyle(doodles.drawStyle.withColor(it.value)) }
                }
                checkBox("Sketchy") {
                    addValueChangeListener { doodles.setRenderMode(if (it.value) RenderMode.SKETCHY else RenderMode.PLAIN) }
                }
                button("Undo") { onClick { doodles.undo() } }
                button("Clear") { onClick { doodles.clear() } }
                button("Toggle margin") {
                    onClick {
                        margin = if (margin == 40) 120 else 40
                        grid.setCellMargin(margin) // relayout: anchored doodles follow, absolute ones stay
                    }
                }
                button("Fit all") { onClick { grid.fitAll() } }
            }
            homeButton()
        }
    }

    init {
        refreshTools()
        grid.bindArrowKeys(this)
        grid.focus()
    }
}
