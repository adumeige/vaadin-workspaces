package org.antoined.demo

import com.github.mvysny.karibudsl.v10.KComposite
import com.github.mvysny.karibudsl.v10.css
import com.github.mvysny.karibudsl.v10.p
import com.github.mvysny.karibudsl.v10.verticalLayout
import com.github.mvysny.karibudsl.v23.card
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.router.Route
import kotlinx.css.background
import org.antoined.workspace.StripWorkspace
import org.antoined.workspace.karibu.item
import org.antoined.workspace.karibu.stripWorkspace
import org.antoined.workspace.layout.LinearLayout

@Route("strip")
class StripView : KComposite() {

    private lateinit var strip: StripWorkspace

    private val root = ui {
        verticalLayout(padding = false, spacing = false) {
            setSizeFull()
            strip = stripWorkspace(LinearLayout.Orientation.HORIZONTAL, itemLength = 500.0, crossSize = 600.0, gap = 24.0) {
                setSize("100%", "100%")
                css { background = "#f0f0f0" }
                setZoomingEnabled(false)
                for (i in 1..8) {
                    item(length = if (i % 3 == 0) 800.0 else 500.0) {
                        card {
                            header = H3("Step $i")
                            setSizeFull()
                            p("Item $i of the strip; widths alternate to show variable lengths.")
                        }
                    }
                }
                addItemFocusedListener { println("Strip item ${it.index}") }
                setInitialFit(0)
            }
            homeButton()
        }
    }

    init {
        strip.bindArrowKeys(this)
        strip.focus()
    }
}
