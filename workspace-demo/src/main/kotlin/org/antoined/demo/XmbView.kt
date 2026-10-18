package org.antoined.demo

import com.github.mvysny.karibudsl.v10.KComposite
import com.github.mvysny.karibudsl.v10.css
import com.github.mvysny.karibudsl.v10.div
import com.github.mvysny.karibudsl.v10.h4
import com.github.mvysny.karibudsl.v10.span
import com.github.mvysny.karibudsl.v10.verticalLayout
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.router.Route
import kotlinx.css.Align
import kotlinx.css.Color
import kotlinx.css.Cursor
import kotlinx.css.Display
import kotlinx.css.JustifyContent
import kotlinx.css.alignItems
import kotlinx.css.background
import kotlinx.css.borderRadius
import kotlinx.css.color
import kotlinx.css.cursor
import kotlinx.css.display
import kotlinx.css.justifyContent
import kotlinx.css.paddingLeft
import kotlinx.css.px
import org.antoined.workspace.karibu.categoryRenderer
import org.antoined.workspace.karibu.itemRenderer
import org.antoined.workspace.karibu.xMediaBar
import org.antoined.workspace.xmb.XMediaBar
import org.antoined.workspace.xmb.XmbLayout

private data class Category(val id: String, val label: String, val color: String)
private data class Item(val id: String, val label: String, val enabled: Boolean = true)

private val categories = listOf(
    Category("settings", "Settings", "#4a5568"),
    Category("photos", "Photos", "#d69e2e"),
    Category("music", "Music", "#38a169"),
    Category("video", "Video", "#3182ce"),
    Category("games", "Games", "#805ad5"),
    Category("network", "Network", "#dd6b20"),
)
private val items = mapOf(
    "settings" to (1..6).map { Item("s$it", "Setting $it") },
    "photos" to (1..12).map { Item("p$it", "Album $it") },
    "music" to listOf(Item("m1", "Playlists"), Item("m2", "Artists"), Item("m3", "Albums", enabled = false), Item("m4", "Songs")),
    "video" to emptyList(),
    "games" to (1..40).map { Item("g$it", "Game $it", enabled = it % 7 != 0) },
    "network" to listOf(Item("n1", "Browser")),
)

@Route("xmb")
class XmbView : KComposite() {

    private lateinit var bar: XMediaBar<Category, Item>

    private val root = ui {
        verticalLayout(padding = false, spacing = false) {
            setSizeFull()
            bar = xMediaBar<Category, Item> {
                setSize("100%", "100%")
                css { background = "linear-gradient(135deg, #1a202c, #2d3748)" }
                xmbLayout.categorySize(140.0, 110.0).categoryGap(50.0).itemSize(260.0, 64.0).itemGap(10.0).headerGap(20.0)
                    .itemAlignment(XmbLayout.ItemAlignment.START)
                setAnchor(0.3, 0.3)
                setRenderWindow(5, 8) // every category stays rendered; item columns are bounded
                setCategories(categories)
                setItemProvider { items[it.id] ?: emptyList() }
                setFocusable { it.enabled }
                categoryRenderer { c ->
                    div {
                        setSizeFull()
                        css {
                            background = c.color; color = Color.white; borderRadius = 12.px
                            display = Display.flex; alignItems = Align.center; justifyContent = JustifyContent.center
                        }
                        h4(c.label)
                    }
                }
                itemRenderer { c, i ->
                    div {
                        setSizeFull()
                        css {
                            background = "rgba(255,255,255,0.12)"; color = Color.white
                            put("border-left", "4px solid ${c.color}"); borderRadius = 6.px
                            display = Display.flex; alignItems = Align.center; paddingLeft = 16.px
                            cursor = Cursor.pointer
                        }
                        span(i.label)
                    }
                }
                addItemActivatedListener { Notification.show("Activated ${it.item.label} in ${it.category.label}") }
                addBackListener { Notification.show("Back") }
                addItemFocusedListener { println("Focused ${it.item?.label} in ${it.category.label}") }
                refresh()
            }
            homeButton()
        }
    }

    init {
        bar.bindKeys(this)
        bar.focus()
    }
}
