package org.antoined.demo

import com.vaadin.flow.component.card.Card
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.html.Paragraph
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.router.Route
import org.antoined.workspace.StripWorkspace
import org.antoined.workspace.layout.LinearLayout

@Route("strip")
class StripView : VerticalLayout() {

    init {
        setSizeFull()
        isPadding = false
        isSpacing = false

        val strip = StripWorkspace(LinearLayout.Orientation.HORIZONTAL, 500.0, 600.0, 24.0)
        strip.setSize("100%", "100%")
        strip.element.style.set("background", "#f0f0f0")
        strip.setZoomingEnabled(false)

        for (i in 1..8) {
            val card = Card().apply {
                header = H3("Step $i")
                setSizeFull()
                add(Paragraph("Item $i of the strip; widths alternate to show variable lengths."))
            }
            strip.append(card, if (i % 3 == 0) 800.0 else 500.0)
        }
        strip.addItemFocusedListener { println("Strip item ${it.index}") }
        strip.setInitialFit(0)

        add(strip)
        strip.bindArrowKeys(this)
        strip.focus()
    }
}
