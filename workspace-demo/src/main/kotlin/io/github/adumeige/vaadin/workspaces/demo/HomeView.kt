package io.github.adumeige.vaadin.workspaces.demo

import com.github.mvysny.karibudsl.v10.KComposite
import com.github.mvysny.karibudsl.v10.css
import com.github.mvysny.karibudsl.v10.div
import com.github.mvysny.karibudsl.v10.h2
import com.github.mvysny.karibudsl.v10.onClick
import com.github.mvysny.karibudsl.v10.p
import com.github.mvysny.karibudsl.v10.span
import com.github.mvysny.karibudsl.v10.verticalLayout
import com.vaadin.flow.component.Component
import com.vaadin.flow.component.UI
import com.vaadin.flow.router.Route
import kotlinx.css.Align
import kotlinx.css.BoxSizing
import kotlinx.css.Cursor
import kotlinx.css.Display
import kotlinx.css.FlexDirection
import kotlinx.css.JustifyContent
import kotlinx.css.Padding
import kotlinx.css.background
import kotlinx.css.borderRadius
import kotlinx.css.boxSizing
import kotlinx.css.cursor
import kotlinx.css.display
import kotlinx.css.flexDirection
import kotlinx.css.fontFamily
import kotlinx.css.justifyContent
import kotlinx.css.opacity
import kotlinx.css.padding
import kotlinx.css.px
import io.github.adumeige.vaadin.workspaces.GridWorkspace
import io.github.adumeige.vaadin.workspaces.KeyBindings
import io.github.adumeige.vaadin.workspaces.karibu.cell
import io.github.adumeige.vaadin.workspaces.karibu.gridWorkspace
import io.github.adumeige.vaadin.workspaces.layout.GridCell

private data class Tile(val col: Int, val row: Int, val color: String, val title: String, val blurb: String, val target: Class<out Component>)

private val tiles = listOf(
    Tile(0, 0, "#3182ce", "Grid", "Cells on a grid, arrows pan one cell at a time.", GridView::class.java),
    Tile(1, 0, "#38a169", "Strip", "A sequence along one axis with per-item lengths.", StripView::class.java),
    Tile(2, 0, "#805ad5", "Free", "Caller-placed rectangles, nearest-neighbour navigation.", FreeView::class.java),
    Tile(0, 1, "#2d3748", "XMediaBar", "Categories across, items down, PlayStation style.", XmbView::class.java),
    Tile(1, 1, "#dd6b20", "Cascade", "Sliding columns whose selections cascade.", CascadeView::class.java),
    Tile(2, 1, "#e53e3e", "Doodles", "Circles, arrows, markers and notes on any workspace.", DoodlesView::class.java),
)

/** The demo's entry point: one grid tile per view. Arrows move between tiles, Enter or a click opens one. */
@Route("")
class HomeView : KComposite() {

    private lateinit var grid: GridWorkspace

    private val root = ui {
        verticalLayout(padding = false, spacing = false) {
            setSizeFull()
            grid = gridWorkspace(480, 300) {
                setSize("100%", "100%")
                setCellMargin(24)
                css { background = "#f7fafc" }
                setZoomingEnabled(false).setDraggingEnabled(false)
                tiles.forEach { t ->
                    cell(t.col, t.row) {
                        div {
                            setSizeFull()
                            css {
                                background = "white"
                                put("border-top", "8px solid ${t.color}")
                                borderRadius = 12.px
                                padding = Padding(24.px, 32.px)
                                boxSizing = BoxSizing.borderBox
                                display = Display.flex
                                flexDirection = FlexDirection.column
                                justifyContent = JustifyContent.center
                                cursor = Cursor.pointer
                                put("box-shadow", "0 2px 12px rgba(0,0,0,0.08)")
                            }
                            h2(t.title)
                            p(t.blurb)
                            span("/${t.target.getAnnotation(Route::class.java).value}") {
                                css { fontFamily = "monospace"; opacity = 0.7 }
                            }
                            onClick { UI.getCurrent().navigate(t.target) }
                        }
                    }
                }
                setInitialFitAll()
            }
        }
    }

    init {
        val byCell = tiles.associateBy { GridCell(it.col, it.row) }
        grid.bindArrowKeys(this)
        KeyBindings.bind(this, listOf("Enter")) { byCell[grid.currentCell]?.let { UI.getCurrent().navigate(it.target) } }
        grid.focus()
    }
}
