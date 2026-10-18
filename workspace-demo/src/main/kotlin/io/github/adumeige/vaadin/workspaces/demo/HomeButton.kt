package io.github.adumeige.vaadin.workspaces.demo

import com.github.mvysny.karibudsl.v10.VaadinDsl
import com.github.mvysny.karibudsl.v10.button
import com.github.mvysny.karibudsl.v10.css
import com.github.mvysny.karibudsl.v10.onClick
import com.vaadin.flow.component.HasComponents
import com.vaadin.flow.component.UI
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.icon.VaadinIcon
import kotlinx.css.Color
import kotlinx.css.Position
import kotlinx.css.background
import kotlinx.css.borderRadius
import kotlinx.css.color
import kotlinx.css.left
import kotlinx.css.pct
import kotlinx.css.position
import kotlinx.css.px
import kotlinx.css.top
import kotlinx.css.zIndex

/** A floating "Home" button pinned to the top-left corner of the page, leading back to [HomeView]. */
@VaadinDsl
fun (@VaadinDsl HasComponents).homeButton(): Button = button(icon = VaadinIcon.HOME.create()) {
    addThemeVariants(ButtonVariant.LUMO_ICON)
    element.setAttribute("aria-label", "Home")
    element.setAttribute("title", "Home")
    // Explicit colours so the button reads on light and dark workspaces alike.
    css {
        position = Position.fixed
        top = 16.px
        left = 16.px
        zIndex = 20
        background = "white"
        color = Color("#2d3748")
        borderRadius = 50.pct
        put("border", "1px solid #cbd5e0")
        put("box-shadow", "0 2px 8px rgba(0,0,0,0.25)")
    }
    onClick { UI.getCurrent().navigate(HomeView::class.java) }
}
