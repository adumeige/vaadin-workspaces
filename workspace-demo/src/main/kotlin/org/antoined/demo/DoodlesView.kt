package org.antoined.demo

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.card.Card
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.html.Paragraph
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.select.Select
import com.vaadin.flow.router.Route
import org.antoined.workspace.GridWorkspace
import org.antoined.workspace.Placement
import org.antoined.workspace.doodles.Anchor
import org.antoined.workspace.doodles.Doodle
import org.antoined.workspace.doodles.DoodleLayer
import org.antoined.workspace.doodles.DoodleStyle
import org.antoined.workspace.doodles.DoodleTool
import org.antoined.workspace.doodles.RenderMode
import org.antoined.workspace.layout.GridCell

@Route("doodles")
class DoodlesView : VerticalLayout() {

    init {
        setSizeFull()
        isPadding = false
        isSpacing = false

        val grid = GridWorkspace(600, 400)
        grid.setSize("100%", "100%")
        grid.setCellMargin(40)
        grid.element.style.set("background", "#f0f0f0")

        val lorem = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua."
        for (col in 0..2) for (row in 0..1) {
            grid.addCell(col, row, Card().apply {
                header = H3("Cell ($col,$row)")
                setSizeFull()
                add(Paragraph(lorem), Paragraph("Select some of this text with the text-marker tool to highlight it."))
            })
        }

        val doodles = DoodleLayer(grid, Placement.ABOVE)
        doodles.addDoodleAddedListener { println("Doodle added (${if (it.isFromClient) "client" else "server"}): ${it.doodle}") }
        doodles.addDoodleRemovedListener { println("Doodle removed: ${it.doodle.id()}") }
        // Select mode: clicking a doodle toggles its own render mode (per-doodle override).
        doodles.addDoodleClickedListener { e ->
            val current = e.doodle.style().render()
            val effective = if (current == RenderMode.DEFAULT) doodles.renderMode else current
            val next = if (effective == RenderMode.SKETCHY) RenderMode.PLAIN else RenderMode.SKETCHY
            doodles.restyle(e.doodle.id(), e.doodle.style().withRender(next))
            Notification.show("Doodle ${e.doodle.id()} now ${next.name.lowercase()}")
        }

        // Programmatic doodles: an item-anchored arrow, an absolute ellipse and a note.
        doodles.add(Doodle.arrow(Anchor.on(GridCell(0, 0), 1.0, 0.5), Anchor.on(GridCell(1, 0), 0.0, 0.5)))
        doodles.add(Doodle.ellipse(Anchor.at(300.0, 200.0), 200.0, 120.0).withStyle(DoodleStyle.DEFAULT.withColor("#3182ce").withDashed(true)))
        doodles.add(Doodle.note(Anchor.on(GridCell(2, 1), 0.6, 0.1), 220.0, 110.0, "A sticky note anchored to cell (2,1). Drag the margin slider to see it follow."))

        val toolbar = HorizontalLayout().apply { isPadding = true; isSpacing = true; isWrap = true }
        val toolButtons = mutableMapOf<DoodleTool, Button>()
        fun refreshTools() = toolButtons.forEach { (t, b) ->
            if (t == doodles.tool) b.addThemeVariants(ButtonVariant.LUMO_PRIMARY) else b.removeThemeVariants(ButtonVariant.LUMO_PRIMARY)
        }
        listOf(
            DoodleTool.NONE to "Select", DoodleTool.ELLIPSE to "Circle", DoodleTool.RECT to "Highlight",
            DoodleTool.ARROW to "Arrow", DoodleTool.PEN to "Pen", DoodleTool.MARKER to "Marker",
            DoodleTool.TEXT_MARKER to "Text marker", DoodleTool.TEXT to "Text", DoodleTool.NOTE to "Note",
            DoodleTool.ERASER to "Eraser",
        ).forEach { (tool, label) ->
            toolButtons[tool] = Button(label) { doodles.tool = tool; refreshTools() }.also { toolbar.add(it) }
        }
        doodles.addToolChangedListener { refreshTools() }

        val color = Select<String>().apply {
            setItems("#e53e3e", "#3182ce", "#38a169", "#d69e2e", "#805ad5", "#f6e05e")
            value = "#e53e3e"
            addValueChangeListener { doodles.setDrawStyle(doodles.drawStyle.withColor(it.value)) }
        }
        toolbar.add(color)
        toolbar.add(Checkbox("Sketchy").apply {
            addValueChangeListener { doodles.setRenderMode(if (it.value) RenderMode.SKETCHY else RenderMode.PLAIN) }
        })
        toolbar.add(Button("Undo") { doodles.undo() })
        toolbar.add(Button("Clear") { doodles.clear() })
        var margin = 40
        toolbar.add(Button("Toggle margin") {
            margin = if (margin == 40) 120 else 40
            grid.setCellMargin(margin) // relayout: anchored doodles follow, absolute ones stay
        })
        toolbar.add(Button("Fit all") { grid.fitAll() })
        refreshTools()

        add(grid, toolbar)
        grid.setInitialFitAll()
        grid.bindArrowKeys(this)
    }
}
