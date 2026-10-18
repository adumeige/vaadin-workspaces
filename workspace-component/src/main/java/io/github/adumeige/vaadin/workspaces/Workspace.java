package io.github.adumeige.vaadin.workspaces;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.DomEvent;
import com.vaadin.flow.component.EventData;
import com.vaadin.flow.component.Focusable;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.shared.Registration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A flat surface of items under a moving viewport. This class is a container with no
 * navigation semantics of its own: it stores items, asks a {@link WorkspaceLayout} where
 * they go, and drives the client viewport. Behaviours (what "next" means, what is focused,
 * which keys do what) belong to leaf components built on top of it.
 *
 * @param <K> item key type
 */
@Tag("workspace-viewport")
@JsModule("./workspace-viewport.ts")
public class Workspace<K> extends Component implements Focusable<Workspace<K>> {

    private final WorkspaceLayout<K> layout;
    private final Map<K, Div> items = new LinkedHashMap<>();
    private final Map<K, Integer> itemIds = new HashMap<>();
    private final Map<Integer, K> keysById = new HashMap<>();
    private final List<WorkspaceLayer<K>> layers = new ArrayList<>();
    private int nextItemId = 1;
    private boolean animateItems = false;
    private ViewState lastView;

    public Workspace(WorkspaceLayout<K> layout) {
        this.layout = layout;
        getElement().setAttribute("tabindex", "0");
        addListener(ViewChangedEvent.class, e -> lastView = e.getView());
    }

    public WorkspaceLayout<K> getLayout() {
        return layout;
    }

    // ── Items ────────────────────────────────────────────────────────────

    public Workspace<K> addItem(K key, Component component) {
        detach(key);
        Div wrapper = new Div(component);
        Element el = wrapper.getElement();
        el.setAttribute("data-item", "");
        if (animateItems) el.setAttribute("data-animate", "");
        int id = nextItemId++;
        itemIds.put(key, id);
        keysById.put(id, key);
        el.setAttribute("data-item-id", String.valueOf(id));
        items.put(key, wrapper);
        applyBounds(key, wrapper);
        getElement().appendChild(el);
        return this;
    }

    public Workspace<K> removeItem(K key) {
        detach(key);
        return this;
    }

    private void detach(K key) {
        Div existing = items.remove(key);
        if (existing != null) {
            existing.getElement().removeFromParent();
        }
        Integer id = itemIds.remove(key);
        if (id != null) keysById.remove(id);
    }

    /** The stable client-side id of an item (its {@code data-item-id} attribute). */
    public Optional<Integer> itemId(K key) {
        return Optional.ofNullable(itemIds.get(key));
    }

    /** The key behind a client-side item id. */
    public Optional<K> itemKey(int id) {
        return Optional.ofNullable(keysById.get(id));
    }

    /** The absolute point at fraction (fx, fy) of an item's bounds. */
    public Optional<Point> resolve(K key, double fx, double fy) {
        if (!items.containsKey(key)) return Optional.empty();
        Rect r = layout.bounds(key);
        return Optional.of(new Point(r.x() + r.width() * fx, r.y() + r.height() * fy));
    }

    /** Bounds of an item, if present. */
    public Optional<Rect> bounds(K key) {
        return items.containsKey(key) ? Optional.of(layout.bounds(key)) : Optional.empty();
    }

    public boolean hasItem(K key) {
        return items.containsKey(key);
    }

    public Set<K> keys() {
        return Collections.unmodifiableSet(items.keySet());
    }

    public int itemCount() {
        return items.size();
    }

    /** The wrapper element of an item, for leaves to tag with state classes or ARIA attributes. */
    public Optional<Element> itemElement(K key) {
        Div d = items.get(key);
        return d == null ? Optional.empty() : Optional.of(d.getElement());
    }

    public Optional<Component> itemComponent(K key) {
        Div d = items.get(key);
        return d == null ? Optional.empty() : d.getChildren().findFirst();
    }

    /** Re-read bounds from the layout for every item, then notify layers. */
    public void relayout() {
        items.forEach(this::applyBounds);
        notifyLayers();
    }

    /** Re-read bounds from the layout for the given items only, then notify layers. */
    public void relayout(Collection<K> keys) {
        for (K key : keys) {
            Div d = items.get(key);
            if (d != null) applyBounds(key, d);
        }
        notifyLayers();
    }

    private void notifyLayers() {
        for (WorkspaceLayer<K> layer : new ArrayList<>(layers)) layer.onRelayout(this);
    }

    // ── Layers ───────────────────────────────────────────────────────────

    /**
     * Add a layer under or over the items. The layer lives in the same transformed canvas, so it
     * pans and zooms with the surface. If it implements {@link WorkspaceLayer} it is told about
     * relayouts.
     */
    public Workspace<K> addLayer(Component layer, Placement placement) {
        layer.getElement().setAttribute("slot", placement == Placement.BELOW ? "below" : "above");
        getElement().appendChild(layer.getElement());
        if (layer instanceof WorkspaceLayer<?> wl) {
            @SuppressWarnings("unchecked") WorkspaceLayer<K> typed = (WorkspaceLayer<K>) wl;
            layers.add(typed);
        }
        return this;
    }

    public Workspace<K> removeLayer(Component layer) {
        layer.getElement().removeFromParent();
        if (layer instanceof WorkspaceLayer<?> wl) layers.remove(wl);
        return this;
    }

    private void applyBounds(K key, Div wrapper) {
        Rect r = layout.bounds(key);
        Element el = wrapper.getElement();
        el.setAttribute("data-x", fmt(r.x()));
        el.setAttribute("data-y", fmt(r.y()));
        el.setAttribute("data-w", fmt(r.width()));
        el.setAttribute("data-h", fmt(r.height()));
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** When enabled, item moves caused by {@link #relayout()} are animated on the client. */
    public Workspace<K> setAnimateItems(boolean animate) {
        this.animateItems = animate;
        items.values().forEach(d -> {
            if (animate) d.getElement().setAttribute("data-animate", "");
            else d.getElement().removeAttribute("data-animate");
        });
        return this;
    }

    // ── Viewport ─────────────────────────────────────────────────────────

    public void fitTo(K key) {
        fitRect(layout.bounds(key), 0);
    }

    /** Fit the union of the given items' bounds, with padding in workspace units. */
    public void fitTo(Collection<K> keys, double padding) {
        if (keys.isEmpty()) return;
        fitRect(layout.extent(keys), padding);
    }

    public void zoomTo(K key) {
        zoomToRect(layout.bounds(key));
    }

    /** Zoom out to everything, then fit the union of the given items' bounds. */
    public void zoomTo(Collection<K> keys) {
        if (keys.isEmpty()) return;
        zoomToRect(layout.extent(keys));
    }

    /** Fit an arbitrary rectangle, with padding in workspace units. */
    public void fitRect(Rect r, double padding) {
        getElement().callJsFunction("fitRect", r.x(), r.y(), r.width(), r.height(), padding);
    }

    public void zoomToRect(Rect r) {
        getElement().callJsFunction("zoomToRect", r.x(), r.y(), r.width(), r.height());
    }

    public void fitAll() {
        getElement().callJsFunction("fitAll");
    }

    public void setView(ViewState view) {
        setView(view.centerX(), view.centerY(), view.scale());
    }

    public void setView(double centerX, double centerY, double scale) {
        getElement().callJsFunction("setView", centerX, centerY, scale);
    }

    /** Place workspace point (px, py) at viewport fraction (fx, fy), 0..1 on each axis. */
    public void anchorPoint(double px, double py, double fx, double fy, double scale) {
        getElement().callJsFunction("anchorPoint", px, py, fx, fy, scale);
    }

    /** Centre on a point keeping the current scale. */
    public void centerOn(double cx, double cy) {
        getElement().callJsFunction("centerOn", cx, cy);
    }

    public void panBy(double dx, double dy) {
        getElement().callJsFunction("panBy", dx, dy);
    }

    /** The last view reported by the client, if any. */
    public Optional<ViewState> getLastView() {
        return Optional.ofNullable(lastView);
    }

    public Workspace<K> setInitialFitAll() {
        getElement().setProperty("initialFitAll", true);
        return this;
    }

    public Workspace<K> setInitialView(ViewState view) {
        getElement().executeJs("this.initialView = {cx: $0, cy: $1, scale: $2}",
                view.centerX(), view.centerY(), view.scale());
        return this;
    }

    /** Fit the given item once the viewport has a size. */
    public Workspace<K> setInitialFit(K key) {
        Rect r = layout.bounds(key);
        getElement().executeJs("this.initialFit = {x: $0, y: $1, w: $2, h: $3}", r.x(), r.y(), r.width(), r.height());
        return this;
    }

    public Workspace<K> setDraggingEnabled(boolean enabled) {
        getElement().setProperty("draggingEnabled", enabled);
        return this;
    }

    public Workspace<K> setZoomingEnabled(boolean enabled) {
        getElement().setProperty("zoomingEnabled", enabled);
        return this;
    }

    public Workspace<K> setScaleRange(double min, double max) {
        getElement().setProperty("minScale", min);
        getElement().setProperty("maxScale", max);
        return this;
    }

    public Workspace<K> setFitPadding(int px) {
        getElement().setProperty("fitPadding", px);
        return this;
    }

    public Workspace<K> setTransitionDuration(int ms) {
        getElement().setProperty("transitionDuration", ms);
        return this;
    }

    public Workspace<K> setSize(String width, String height) {
        getElement().getStyle().set("width", width).set("height", height);
        return this;
    }

    // ── Events ───────────────────────────────────────────────────────────

    @DomEvent("workspace-view-changed")
    public static class ViewChangedEvent extends ComponentEvent<Workspace<?>> {
        private final ViewState view;

        public ViewChangedEvent(Workspace<?> source, boolean fromClient,
                                @EventData("event.detail.cx") double cx,
                                @EventData("event.detail.cy") double cy,
                                @EventData("event.detail.scale") double scale) {
            super(source, fromClient);
            this.view = new ViewState(cx, cy, scale);
        }

        public ViewState getView() { return view; }
    }

    public Registration addViewChangedListener(ComponentEventListener<ViewChangedEvent> listener) {
        return addListener(ViewChangedEvent.class, listener);
    }
}
