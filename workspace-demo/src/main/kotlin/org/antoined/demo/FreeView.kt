package org.antoined.demo

import com.vaadin.flow.component.card.Card
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.html.Paragraph
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.router.Route
import org.antoined.workspace.FreeWorkspace

@Route("free")
class FreeView : VerticalLayout() {

    init {
        setSizeFull()
        isPadding = false
        isSpacing = false

        val free = FreeWorkspace<String>()
        free.setSize("100%", "100%")
        free.element.style.set("background", "#f0f0f0")

        fun card(title: String) = Card().apply {
            header = H3(title)
            setSizeFull()
            add(Paragraph("Arrow keys jump to the nearest neighbour in that direction."))
        }
        free.place("hub", 0.0, 0.0, 400.0, 300.0, card("Hub"))
        free.place("north", 50.0, -500.0, 300.0, 300.0, card("North"))
        free.place("east", 700.0, 80.0, 500.0, 200.0, card("East (wide)"))
        free.place("south-east", 600.0, 500.0, 300.0, 400.0, card("South-east"))
        free.place("west", -600.0, 0.0, 300.0, 300.0, card("West"))
        free.addItemFocusedListener { println("Free item ${it.key}") }
        free.setInitialFitAll()

        add(free)
        free.bindArrowKeys(this)
        free.focus()
    }
}
