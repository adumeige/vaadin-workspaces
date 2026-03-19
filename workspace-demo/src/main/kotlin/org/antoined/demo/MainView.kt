package org.antoined.demo

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.card.Card
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.html.Paragraph
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.router.Route
import org.antoined.workspace.GridWorkspace

private data class CellDef(val col: Int, val row: Int, val bg: String, val title: String)

@Route("")
class MainView : VerticalLayout() {

    init {
        setSizeFull()
        isPadding = false
        isSpacing = false

        val gridWorkspace = GridWorkspace(800, 600)
        gridWorkspace.setSize("100%", "100%")
        gridWorkspace.setCellMargin(12)
        gridWorkspace.element.style
            .set("background", "#f0f0f0")
            .set("border", "1px solid #ccc")

        gridWorkspace.addCellFocusedListener { println("Focused cell: ${it.col},  ${it.row}") }
        gridWorkspace.setZoomingEnabled(false).setDraggingEnabled(false)


        val cellDefs = listOf(
            CellDef(0, 0, "#ffd6d6", "Cell (0,0)"),
            CellDef(1, 0, "#d6ffd6", "Cell (1,0)"),
            CellDef(2, 0, "#d6d6ff", "Cell (2,0)"),
            CellDef(0, 1, "#ffffd6", "Cell (0,1)"),
            CellDef(1, 1, "#ffd6ff", "Cell (1,1)"),
            CellDef(2, 1, "#d6ffff", "Cell (2,1)")
        )

        cellDefs.forEach { def ->
            val card = Card().apply {
                header = H3(def.title)
                setSizeFull()

                add(Paragraph("Column ${def.col}, Row ${def.row}"))
                add(TextField("Text field"))
            }
            gridWorkspace.addCell(def.col, def.row, card)
        }

        val buttonBar = HorizontalLayout()
        buttonBar.isPadding = true
        buttonBar.isSpacing = true

        buttonBar.add(Button("Fit All") { gridWorkspace.fitAll() })

        cellDefs.forEach { def ->
            buttonBar.add(Button("Go to ${def.title}") { gridWorkspace.fitToCell(def.col, def.row) })
            buttonBar.add(Button("Zoom to ${def.title}") { gridWorkspace.zoomToCell(def.col, def.row) })
        }

        add(gridWorkspace, buttonBar)
        gridWorkspace.bindArrowKeys(this)
        gridWorkspace.setInitialFitAll()

        gridWorkspace.focus()
    }
}
