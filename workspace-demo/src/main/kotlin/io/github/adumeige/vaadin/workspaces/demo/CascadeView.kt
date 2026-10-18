package io.github.adumeige.vaadin.workspaces.demo

import com.github.mvysny.karibudsl.v10.KComposite
import com.github.mvysny.karibudsl.v10.button
import com.github.mvysny.karibudsl.v10.css
import com.github.mvysny.karibudsl.v10.div
import com.github.mvysny.karibudsl.v10.h4
import com.github.mvysny.karibudsl.v10.horizontalLayout
import com.github.mvysny.karibudsl.v10.init
import com.github.mvysny.karibudsl.v10.onClick
import com.github.mvysny.karibudsl.v10.span
import com.github.mvysny.karibudsl.v10.verticalLayout
import com.vaadin.flow.component.HasComponents
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.upload.Upload
import com.vaadin.flow.router.Route
import com.vaadin.flow.server.streams.UploadHandler
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
import kotlinx.css.justifyContent
import kotlinx.css.padding
import kotlinx.css.px
import io.github.adumeige.vaadin.workspaces.cascade.CascadeWorkspace
import io.github.adumeige.vaadin.workspaces.cascade.SlidingColumn
import io.github.adumeige.vaadin.workspaces.karibu.cascadeWorkspace
import io.github.adumeige.vaadin.workspaces.karibu.column

/** The demo's only first-column element: a card to drop or pick files on. */
private object DropSlot

private data class Case(val name: String, val files: List<String>)
private data class Doc(val name: String)
private data class Note(val text: String)

/** A labelled card with a coloured left edge, the look shared by every column. */
private fun HasComponents.caseCard(title: String, subtitle: String, color: String) = div {
    setSizeFull()
    css {
        background = "white"; put("border-left", "6px solid $color"); borderRadius = 8.px
        padding = Padding(12.px, 16.px); boxSizing = BoxSizing.borderBox; display = Display.flex
        flexDirection = FlexDirection.column; justifyContent = JustifyContent.center; cursor = Cursor.pointer
    }
    h4(title)
    span(subtitle)
}

/** Drop zone and button; every finished batch of files is handed over as one case. */
private fun HasComponents.dropCard(onFiles: (List<String>) -> Unit) = div {
    setSizeFull()
    css { put("border", "2px dashed #a0aec0"); borderRadius = 8.px; boxSizing = BoxSizing.borderBox }
    val batch = mutableListOf<String>()
    init(Upload(UploadHandler.inMemory { meta, _ -> batch.add(meta.fileName()) })) {
        isDropAllowed = true
        isAutoUpload = true
        uploadButton = Button("Open files")
        dropLabel = Span("Drop a folder or files here")
        setSizeFull()
        addAllFinishedListener {
            if (batch.isNotEmpty()) {
                val files = batch.toList()
                batch.clear()
                clearFileList()
                onFiles(files)
            }
        }
    }
}

/**
 * Four columns: a drop card, the opened cases, and two columns derived from the selected case.
 * The column shape is entirely this view's choice; the component takes any number of columns.
 */
@Route("cascade")
class CascadeView : KComposite() {

    private lateinit var ws: CascadeWorkspace
    private lateinit var cases: SlidingColumn<Case>

    // Demo-only viewport policy: pan (same scale) onto the anchor row of a pair of adjacent
    // columns: the active one and its right neighbour, or the last two at the right edge.
    private fun pair() = minOf(ws.activeColumn, ws.columnCount() - 2).let { it to it + 1 }
    private fun pan() {
        val (a, b) = pair()
        val band = ws.layout.band(a, b, 1)
        ws.centerOn(band.centerX(), band.centerY())
    }
    private fun zoomTo(scale: Double) {
        val (a, b) = pair()
        val band = ws.layout.band(a, b, 1)
        ws.setView(band.centerX(), band.centerY(), scale)
    }
    private fun currentScale() = ws.lastView.map { it.scale() }.orElse(1.0)
    private fun reset() { val (a, b) = pair(); ws.fitRect(ws.layout.band(a, b, 3), 40.0) }

    private val root = ui {
        verticalLayout(padding = false, spacing = false) {
            setSizeFull()
            cascadeWorkspace {
                ws = this // listeners below pan through `ws` while the block is still running
                setSize("100%", "100%")
                css { background = "#eef1f5" }
                layout.columnSize(320.0, 40.0).itemSize(120.0, 16.0)
                setVisibleRows(3)
                setViewMode(CascadeWorkspace.ViewMode.MANUAL)

                val slots = column<DropSlot> { dropCard { files -> ws.open(cases, Case(files.first().substringBeforeLast('.'), files)) } }
                cases = column<Case> { c -> caseCard(c.name, "${c.files.size} files", "#3182ce") }
                val docs = column<Doc> { d -> caseCard(d.name, "document", "#38a169") }
                val notes = column<Note> { n -> caseCard(n.text, "note", "#dd6b20") }
                derive(cases, docs) { c -> c.files.map { Doc(it) } }
                derive(cases, notes) { c -> listOf(Note("${c.files.size} files"), Note("Opened as ${c.name}"), Note("${c.name} ready")) }

                addSelectionChangedListener { println("Column ${it.column} selected ${it.item}") }
                addColumnActivatedListener { println("Active column ${it.column}") }
                addItemActivatedListener { Notification.show("Activated ${it.item} in column ${it.column}") }
                addItemOpenedListener { Notification.show("Opened ${(it.item as Case).name}") }
                addColumnActivatedListener { pan() }
                addSelectionChangedListener { pan() }

                slots.setItems(listOf(DropSlot))
                refresh()
                open(cases, Case("Alpha", listOf("brief.pdf", "contract.docx", "photo.jpg")))
                open(cases, Case("Beta", listOf("scan.pdf")))
                open(cases, Case("Gamma", (1..9).map { "page$it.png" }))
                cases.first()
            }
            // Zoom controls: the component has mouse zoom off, so the scale is set explicitly here,
            // keeping the current pair of columns centred.
            horizontalLayout(padding = true) {
                button("−") { onClick { zoomTo(currentScale() / 1.25) } }
                button("+") { onClick { zoomTo(currentScale() * 1.25) } }
                listOf(0.5, 0.75, 1.0, 1.5).forEach { z -> button("${(z * 100).toInt()}%") { onClick { zoomTo(z) } } }
                button("Reset") { onClick { reset() } }
            }
            setFlexGrow(1.0, ws)
            homeButton()
        }
    }

    init {
        reset() // initial scale; later moves only pan
        ws.bindKeys(this)
        ws.focus()
    }
}
