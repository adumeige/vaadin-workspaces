package org.antoined.demo

import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.H4
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.router.Route
import org.antoined.workspace.xmb.XMediaBar
import org.antoined.workspace.xmb.XmbLayout

private data class Category(val id: String, val label: String, val color: String)
private data class Item(val id: String, val label: String, val enabled: Boolean = true)

@Route("xmb")
class XmbView : VerticalLayout() {

    init {
        setSizeFull()
        isPadding = false
        isSpacing = false

        val categories = listOf(
            Category("settings", "Settings", "#4a5568"),
            Category("photos", "Photos", "#d69e2e"),
            Category("music", "Music", "#38a169"),
            Category("video", "Video", "#3182ce"),
            Category("games", "Games", "#805ad5"),
            Category("network", "Network", "#dd6b20"),
        )
        val items = mapOf(
            "settings" to (1..6).map { Item("s$it", "Setting $it") },
            "photos" to (1..12).map { Item("p$it", "Album $it") },
            "music" to listOf(Item("m1", "Playlists"), Item("m2", "Artists"), Item("m3", "Albums", enabled = false), Item("m4", "Songs")),
            "video" to emptyList(),
            "games" to (1..40).map { Item("g$it", "Game $it", enabled = it % 7 != 0) },
            "network" to listOf(Item("n1", "Browser")),
        )

        val bar = XMediaBar<Category, Item>()
        bar.setSize("100%", "100%")
        bar.element.style.set("background", "linear-gradient(135deg, #1a202c, #2d3748)")
        bar.xmbLayout.categorySize(140.0, 110.0).categoryGap(50.0).itemSize(260.0, 64.0).itemGap(10.0).headerGap(20.0)
            .itemAlignment(XmbLayout.ItemAlignment.START)
        bar.setAnchor(0.3, 0.3)
        bar.setRenderWindow(5, 8) // every category stays rendered; item columns are bounded
        bar.setCategories(categories)
        bar.setItemProvider { items[it.id] ?: emptyList() }
        bar.setFocusable { it.enabled }
        bar.setCategoryRenderer { c ->
            Div(H4(c.label)).apply {
                setSizeFull()
                style.set("background", c.color).set("color", "white").set("border-radius", "12px")
                    .set("display", "flex").set("align-items", "center").set("justify-content", "center")
            }
        }
        bar.setItemRenderer { c, i ->
            Div(Span(i.label)).apply {
                setSizeFull()
                style.set("background", "rgba(255,255,255,0.12)").set("color", "white")
                    .set("border-left", "4px solid ${c.color}").set("border-radius", "6px")
                    .set("display", "flex").set("align-items", "center").set("padding-left", "16px")
                    .set("cursor", "pointer")
            }
        }
        bar.addItemActivatedListener { Notification.show("Activated ${it.item.label} in ${it.category.label}") }
        bar.addBackListener { Notification.show("Back") }
        bar.addItemFocusedListener { println("Focused ${it.item?.label} in ${it.category.label}") }
        bar.refresh()

        add(bar)
        bar.bindKeys(this)
        bar.focus()
    }
}
