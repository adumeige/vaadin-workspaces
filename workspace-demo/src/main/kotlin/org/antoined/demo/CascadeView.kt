package org.antoined.demo

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.H4
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.upload.Upload
import com.vaadin.flow.router.Route
import com.vaadin.flow.server.streams.UploadHandler
import org.antoined.workspace.cascade.CascadeWorkspace

/** The demo's only first-column element: a card to drop or pick files on. */
private object DropSlot

private data class Case(val name: String, val files: List<String>)
private data class Doc(val name: String)
private data class Note(val text: String)

/**
 * Four columns: a drop card, the opened cases, and two columns derived from the selected case.
 * The column shape is entirely this view's choice; the component takes any number of columns.
 */
@Route("cascade")
class CascadeView : VerticalLayout() {

    init {
        setSizeFull()
        isPadding = false
        isSpacing = false

        fun card(title: String, subtitle: String, color: String) = Div(H4(title), Span(subtitle)).apply {
            setSizeFull()
            style.set("background", "white").set("border-left", "6px solid $color").set("border-radius", "8px")
                .set("padding", "12px 16px").set("box-sizing", "border-box").set("display", "flex")
                .set("flex-direction", "column").set("justify-content", "center").set("cursor", "pointer")
        }

        val ws = CascadeWorkspace()
        ws.setSize("100%", "100%")
        ws.element.style.set("background", "#eef1f5")
        ws.layout.columnSize(320.0, 40.0).itemSize(120.0, 16.0)
        ws.setVisibleRows(3)
        ws.setViewMode(CascadeWorkspace.ViewMode.MANUAL)

        val slots = ws.addColumn<DropSlot>()
        val cases = ws.addColumn<Case> { c -> card(c.name, "${c.files.size} files", "#3182ce") }
        val docs = ws.addColumn<Doc> { d -> card(d.name, "document", "#38a169") }
        val notes = ws.addColumn<Note> { n -> card(n.text, "note", "#dd6b20") }
        ws.setRenderer(slots) { dropCard { files -> ws.open(cases, Case(files.first().substringBeforeLast('.'), files)) } }
        ws.derive(cases, docs) { c -> c.files.map { Doc(it) } }
        ws.derive(cases, notes) { c -> listOf(Note("${c.files.size} files"), Note("Opened as ${c.name}"), Note("${c.name} ready")) }

        ws.addSelectionChangedListener { println("Column ${it.column} selected ${it.item}") }
        ws.addColumnActivatedListener { println("Active column ${it.column}") }
        ws.addItemActivatedListener { Notification.show("Activated ${it.item} in column ${it.column}") }
        ws.addItemOpenedListener { Notification.show("Opened ${(it.item as Case).name}") }

        // Demo-only viewport policy: pan (same scale) onto the anchor row of a pair of adjacent
        // columns: the active one and its right neighbour, or the last two at the right edge.
        fun pair() = minOf(ws.activeColumn, ws.columnCount() - 2).let { it to it + 1 }
        fun pan() {
            val (a, b) = pair()
            val band = ws.layout.band(a, b, 1)
            ws.centerOn(band.centerX(), band.centerY())
        }
        ws.addColumnActivatedListener { pan() }
        ws.addSelectionChangedListener { pan() }

        // Zoom controls: the component has mouse zoom off, so the scale is set explicitly here,
        // keeping the current pair of columns centred.
        fun zoomTo(scale: Double) {
            val (a, b) = pair()
            val band = ws.layout.band(a, b, 1)
            ws.setView(band.centerX(), band.centerY(), scale)
        }
        fun currentScale() = ws.lastView.map { it.scale() }.orElse(1.0)
        val zoomBar = HorizontalLayout().apply {
            isSpacing = true
            style.set("padding", "8px 16px")
            add(Button("−") { zoomTo(currentScale() / 1.25) })
            add(Button("+") { zoomTo(currentScale() * 1.25) })
            listOf(0.5, 0.75, 1.0, 1.5).forEach { z -> add(Button("${(z * 100).toInt()}%") { zoomTo(z) }) }
            add(Button("Reset") { val (a, b) = pair(); ws.fitRect(ws.layout.band(a, b, 3), 40.0) })
        }

        slots.setItems(listOf(DropSlot))
        ws.refresh()
        ws.open(cases, Case("Alpha", listOf("brief.pdf", "contract.docx", "photo.jpg")))
        ws.open(cases, Case("Beta", listOf("scan.pdf")))
        ws.open(cases, Case("Gamma", (1..9).map { "page$it.png" }))
        cases.first()
        val a0 = minOf(ws.activeColumn, ws.columnCount() - 2)
        ws.fitRect(ws.layout.band(a0, a0 + 1, 3), 40.0) // initial scale; later moves only pan

        add(ws, zoomBar)
        setFlexGrow(1.0, ws)
        ws.bindKeys(this)
        ws.focus()
    }

    /** Drop zone and button; every finished batch of files is handed over as one case. */
    private fun dropCard(onFiles: (List<String>) -> Unit): Div {
        val batch = mutableListOf<String>()
        val upload = Upload(UploadHandler.inMemory { meta, _ -> batch.add(meta.fileName()) })
        upload.isDropAllowed = true
        upload.isAutoUpload = true
        upload.uploadButton = Button("Open files")
        upload.dropLabel = Span("Drop a folder or files here")
        upload.setSizeFull()
        upload.addAllFinishedListener {
            if (batch.isNotEmpty()) {
                val files = batch.toList()
                batch.clear()
                upload.clearFileList()
                onFiles(files)
            }
        }
        return Div(upload).apply {
            setSizeFull()
            style.set("border", "2px dashed #a0aec0").set("border-radius", "8px").set("box-sizing", "border-box")
        }
    }
}
