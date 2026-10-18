package org.antoined.demo

import com.github.mvysny.karibudsl.v10.KComposite
import com.github.mvysny.karibudsl.v10.css
import com.github.mvysny.karibudsl.v10.p
import com.github.mvysny.karibudsl.v10.verticalLayout
import com.github.mvysny.karibudsl.v23.card
import com.vaadin.flow.component.HasComponents
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.router.Route
import kotlinx.css.background
import org.antoined.workspace.FreeWorkspace
import org.antoined.workspace.karibu.freeWorkspace
import org.antoined.workspace.karibu.place

@Route("free")
class FreeView : KComposite() {

    private lateinit var free: FreeWorkspace<String>

    private fun HasComponents.placeCard(title: String) = card {
        header = H3(title)
        setSizeFull()
        p("Arrow keys jump to the nearest neighbour in that direction.")
    }

    private val root = ui {
        verticalLayout(padding = false, spacing = false) {
            setSizeFull()
            free = freeWorkspace<String> {
                setSize("100%", "100%")
                css { background = "#f0f0f0" }
                place("hub", 0.0, 0.0, 400.0, 300.0) { placeCard("Hub") }
                place("north", 50.0, -500.0, 300.0, 300.0) { placeCard("North") }
                place("east", 700.0, 80.0, 500.0, 200.0) { placeCard("East (wide)") }
                place("south-east", 600.0, 500.0, 300.0, 400.0) { placeCard("South-east") }
                place("west", -600.0, 0.0, 300.0, 300.0) { placeCard("West") }
                addItemFocusedListener { println("Free item ${it.key}") }
                setInitialFitAll()
            }
            homeButton()
        }
    }

    init {
        free.bindArrowKeys(this)
        free.focus()
    }
}
