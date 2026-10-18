package io.github.adumeige.vaadin.workspaces.doodles;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.shared.Registration;
import elemental.json.JsonObject;
import io.github.adumeige.vaadin.workspaces.Placement;
import io.github.adumeige.vaadin.workspaces.Point;
import io.github.adumeige.vaadin.workspaces.Rect;
import io.github.adumeige.vaadin.workspaces.Workspace;
import io.github.adumeige.vaadin.workspaces.WorkspaceLayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An annotation layer over (or under) a workspace's items. Holds the doodle model on the server,
 * resolves anchors and pushes flat geometry to the client; receives shapes the user draws.
 *
 * @param <K> the workspace's key type
 */
@Tag("workspace-doodle-layer")
@JsModule("./workspace-doodle-layer.ts")
@CssImport("./doodles.css")
public class DoodleLayer<K> extends Component implements WorkspaceLayer<K> {

    private final Workspace<K> workspace;
    private final DoodleJson json;
    private final Map<String, Doodle> doodles = new LinkedHashMap<>();
    private final List<String> history = new ArrayList<>();
    private DoodleTool tool = DoodleTool.NONE;
    private DoodleStyle drawStyle = DoodleStyle.DEFAULT;
    private RenderMode renderMode = RenderMode.PLAIN;
    private boolean anchorToItems = true;

    public DoodleLayer(Workspace<K> workspace, Placement placement) {
        this.workspace = workspace;
        this.json = new DoodleJson(new WorkspaceResolver());
        workspace.addLayer(this, placement);
        getElement().setProperty("tool", tool.name());
        getElement().setPropertyJson("drawStyle", DoodleJson.style(drawStyle));

        getElement().addEventListener("doodle-drawn", e -> {
            JsonObject detail = e.getEventData().getObject("event.detail");
            json.fromClient(detail, anchorToItems).map(this::applyTypeDefaults).ifPresent(d -> add(d, true));
        }).addEventData("event.detail");
        getElement().addEventListener("doodle-erased", e -> remove(e.getEventData().getString("event.detail.id"), true))
                .addEventData("event.detail.id");
        getElement().addEventListener("doodle-clicked", e -> {
            Doodle d = doodles.get(e.getEventData().getString("event.detail.id"));
            if (d != null) fireEvent(new DoodleClickedEvent<>(this, d));
        }).addEventData("event.detail.id");
        getElement().addEventListener("doodle-tool-changed", e -> {
            tool = DoodleTool.valueOf(e.getEventData().getString("event.detail.tool"));
            fireEvent(new ToolChangedEvent<>(this, tool, true));
        }).addEventData("event.detail.tool");
    }

    public DoodleLayer(Workspace<K> workspace) {
        this(workspace, Placement.ABOVE);
    }

    /**
     * The client sends the generic draw style with every shape. Markers, highlights and notes have
     * their own width and opacity defaults; only the chosen colour carries over (notes keep theirs).
     */
    private Doodle applyTypeDefaults(Doodle d) {
        String color = d.style().color();
        RenderMode render = d.style().render();
        if (d instanceof Doodle.Marker) return d.withStyle(DoodleStyle.MARKER.withColor(color).withRender(render));
        if (d instanceof Doodle.HighlightRect || d instanceof Doodle.TextHighlight) return d.withStyle(DoodleStyle.HIGHLIGHT.withColor(color).withRender(render));
        if (d instanceof Doodle.Note) return d.withStyle(DoodleStyle.NOTE.withRender(render));
        return d;
    }

    // ── Model ────────────────────────────────────────────────────────────

    public DoodleLayer<K> add(Doodle doodle) {
        add(doodle, false);
        return this;
    }

    private void add(Doodle doodle, boolean fromClient) {
        doodles.put(doodle.id(), doodle);
        history.remove(doodle.id());
        history.add(doodle.id());
        push();
        fireEvent(new DoodleAddedEvent<>(this, doodle, fromClient));
    }

    public DoodleLayer<K> remove(String id) {
        remove(id, false);
        return this;
    }

    private void remove(String id, boolean fromClient) {
        Doodle d = doodles.remove(id);
        history.remove(id);
        if (d != null) {
            push();
            fireEvent(new DoodleRemovedEvent<>(this, d, fromClient));
        }
    }

    /** Remove the most recently added doodle, whoever added it. */
    public Optional<Doodle> undo() {
        if (history.isEmpty()) return Optional.empty();
        String id = history.get(history.size() - 1);
        Doodle d = doodles.get(id);
        remove(id, false);
        return Optional.ofNullable(d);
    }

    public DoodleLayer<K> clear() {
        List<Doodle> removed = new ArrayList<>(doodles.values());
        doodles.clear();
        history.clear();
        push();
        removed.forEach(d -> fireEvent(new DoodleRemovedEvent<>(this, d, false)));
        return this;
    }

    public List<Doodle> doodles() {
        return Collections.unmodifiableList(new ArrayList<>(doodles.values()));
    }

    public Optional<Doodle> get(String id) {
        return Optional.ofNullable(doodles.get(id));
    }

    // ── Tools and style ──────────────────────────────────────────────────

    public DoodleTool getTool() { return tool; }

    public DoodleLayer<K> setTool(DoodleTool tool) {
        if (this.tool == tool) return this;
        this.tool = tool;
        getElement().setProperty("tool", tool.name());
        fireEvent(new ToolChangedEvent<>(this, tool, false));
        return this;
    }

    public DoodleStyle getDrawStyle() { return drawStyle; }

    /** Style applied to the next doodle drawn by the user. */
    public DoodleLayer<K> setDrawStyle(DoodleStyle style) {
        this.drawStyle = style;
        getElement().setPropertyJson("drawStyle", DoodleJson.style(style));
        return this;
    }

    public RenderMode getRenderMode() { return renderMode; }

    /** Layer-wide render mode, used by every doodle whose style says {@link RenderMode#DEFAULT}. */
    public DoodleLayer<K> setRenderMode(RenderMode mode) {
        this.renderMode = mode == RenderMode.DEFAULT ? RenderMode.PLAIN : mode;
        json.setDefaultRender(this.renderMode);
        push();
        return this;
    }

    /** Replace a doodle's style in place (for example to toggle its render mode). */
    public DoodleLayer<K> restyle(String id, DoodleStyle style) {
        Doodle d = doodles.get(id);
        if (d != null) {
            doodles.put(id, d.withStyle(style));
            push();
        }
        return this;
    }

    /** Whether user-drawn points that start on an item are anchored to it (default true). */
    public DoodleLayer<K> setAnchorToItems(boolean anchorToItems) {
        this.anchorToItems = anchorToItems;
        return this;
    }

    // ── Sync ─────────────────────────────────────────────────────────────

    /** Resolve every anchor and send the flat geometry to the client. */
    public void push() {
        getElement().setPropertyJson("doodles", json.toJson(doodles()));
    }

    @Override
    public void onRelayout(Workspace<K> ws) {
        push();
    }

    private final class WorkspaceResolver implements DoodleJson.Resolver {
        @SuppressWarnings("unchecked")
        public Optional<Point> resolve(Object key, double fx, double fy) {
            try { return workspace.resolve((K) key, fx, fy); } catch (ClassCastException e) { return Optional.empty(); }
        }
        @SuppressWarnings("unchecked")
        public Optional<Rect> bounds(Object key) {
            try { return workspace.bounds((K) key); } catch (ClassCastException e) { return Optional.empty(); }
        }
        @SuppressWarnings("unchecked")
        public Optional<Integer> itemId(Object key) {
            try { return workspace.itemId((K) key); } catch (ClassCastException e) { return Optional.empty(); }
        }
        public Optional<Object> key(int itemId) {
            return workspace.itemKey(itemId).map(k -> (Object) k);
        }
    }

    // ── Events ───────────────────────────────────────────────────────────

    public static class DoodleAddedEvent<K> extends ComponentEvent<DoodleLayer<K>> {
        private final Doodle doodle;
        public DoodleAddedEvent(DoodleLayer<K> source, Doodle doodle, boolean fromClient) { super(source, fromClient); this.doodle = doodle; }
        public Doodle getDoodle() { return doodle; }
    }

    public static class DoodleRemovedEvent<K> extends ComponentEvent<DoodleLayer<K>> {
        private final Doodle doodle;
        public DoodleRemovedEvent(DoodleLayer<K> source, Doodle doodle, boolean fromClient) { super(source, fromClient); this.doodle = doodle; }
        public Doodle getDoodle() { return doodle; }
    }

    public static class DoodleClickedEvent<K> extends ComponentEvent<DoodleLayer<K>> {
        private final Doodle doodle;
        public DoodleClickedEvent(DoodleLayer<K> source, Doodle doodle) { super(source, true); this.doodle = doodle; }
        public Doodle getDoodle() { return doodle; }
    }

    public static class ToolChangedEvent<K> extends ComponentEvent<DoodleLayer<K>> {
        private final DoodleTool tool;
        public ToolChangedEvent(DoodleLayer<K> source, DoodleTool tool, boolean fromClient) { super(source, fromClient); this.tool = tool; }
        public DoodleTool getTool() { return tool; }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Registration addDoodleAddedListener(ComponentEventListener<DoodleAddedEvent<K>> l) {
        return addListener((Class) DoodleAddedEvent.class, (ComponentEventListener) l);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Registration addDoodleRemovedListener(ComponentEventListener<DoodleRemovedEvent<K>> l) {
        return addListener((Class) DoodleRemovedEvent.class, (ComponentEventListener) l);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Registration addDoodleClickedListener(ComponentEventListener<DoodleClickedEvent<K>> l) {
        return addListener((Class) DoodleClickedEvent.class, (ComponentEventListener) l);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Registration addToolChangedListener(ComponentEventListener<ToolChangedEvent<K>> l) {
        return addListener((Class) ToolChangedEvent.class, (ComponentEventListener) l);
    }
}
