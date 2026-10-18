package io.github.adumeige.vaadin.workspaces.demo

import com.github.mvysny.karibudsl.v10.KComposite
import com.github.mvysny.karibudsl.v10.button
import com.github.mvysny.karibudsl.v10.css
import com.github.mvysny.karibudsl.v10.horizontalLayout
import com.github.mvysny.karibudsl.v10.onClick
import com.github.mvysny.karibudsl.v10.p
import com.github.mvysny.karibudsl.v10.textField
import com.github.mvysny.karibudsl.v10.verticalLayout
import com.github.mvysny.karibudsl.v23.card
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.router.Route
import kotlinx.css.background
import io.github.adumeige.vaadin.workspaces.GridWorkspace
import io.github.adumeige.vaadin.workspaces.karibu.cell
import io.github.adumeige.vaadin.workspaces.karibu.gridWorkspace

private data class CellDef(val col: Int, val row: Int, val title: String)

private val cellDefs = listOf(
    CellDef(0, 0, "Cell (0,0)"), CellDef(1, 0, "Cell (1,0)"), CellDef(2, 0, "Cell (2,0)"),
    CellDef(0, 1, "Cell (0,1)"), CellDef(1, 1, "Cell (1,1)"), CellDef(2, 1, "Cell (2,1)"),
)

@Route("grid")
class GridView : KComposite() {

    private lateinit var grid: GridWorkspace

    private val root = ui {
        verticalLayout(padding = false, spacing = false) {
            setSizeFull()
            grid = gridWorkspace(800, 600) {
                setSize("100%", "100%")
                setCellMargin(12)
                css { background = "#f0f0f0"; put("border", "1px solid #ccc") }
                setZoomingEnabled(false).setDraggingEnabled(false)
                addCellFocusedListener { println("Focused cell: ${it.col},  ${it.row}") }
                cellDefs.forEach { def ->
                    cell(def.col, def.row) {
                        card {
                            header = H3(def.title)
                            setSizeFull()
                            p("Column ${def.col}, Row ${def.row}")
                            textField("Text field")
                        }
                    }
                }
                setInitialFitAll()
            }
            horizontalLayout(padding = true) {
                button("Fit All") { onClick { grid.fitAll() } }
                cellDefs.forEach { def ->
                    button("Go to ${def.title}") { onClick { grid.fitToCell(def.col, def.row) } }
                    button("Zoom to ${def.title}") { onClick { grid.zoomToCell(def.col, def.row) } }
                }
            }
            homeButton()
        }
    }

    init {
        grid.bindArrowKeys(this)
        grid.focus()
    }
}
