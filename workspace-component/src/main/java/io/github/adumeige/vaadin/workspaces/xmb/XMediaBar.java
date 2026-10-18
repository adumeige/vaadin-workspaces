package io.github.adumeige.vaadin.workspaces.xmb;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.shared.Registration;
import io.github.adumeige.vaadin.workspaces.ComponentProvider;
import io.github.adumeige.vaadin.workspaces.KeyBindings;
import io.github.adumeige.vaadin.workspaces.Rect;
import io.github.adumeige.vaadin.workspaces.Workspace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A leaf: {@link Workspace} + {@link XmbLayout} + {@link XmbNavigator} + renderers + behaviours.
 * A PlayStation-style cross media bar: categories across, items of the active category down.
 *
 * Horizontal navigation moves the viewport so the active category sits at a stable anchor.
 * Vertical navigation relayouts the active column so the focused item sits under the row.
 * Both are animated by the client with the same duration, giving one coordinated transition.
 *
 * @param <C> category identifier
 * @param <I> item identifier
 */
@CssImport("./xmb.css")
public class XMediaBar<C, I> extends Workspace<XmbKey> {

    private static final Map<String, NavigationCommand> KEYS = Map.of(
            "ArrowLeft", NavigationCommand.PREVIOUS_CATEGORY,
            "ArrowRight", NavigationCommand.NEXT_CATEGORY,
            "ArrowUp", NavigationCommand.PREVIOUS_ITEM,
            "ArrowDown", NavigationCommand.NEXT_ITEM,
            "Enter", NavigationCommand.ACTIVATE,
            "Escape", NavigationCommand.BACK);

    private final XmbNavigator<C, I> navigator;
    private final XmbLayout<C, I> xmb;

    private List<C> categories = List.of();
    private Function<C, List<I>> itemsOf = c -> List.of();
    private Predicate<I> focusable = i -> true;
    private ComponentProvider<C> categoryRenderer = c -> new com.vaadin.flow.component.html.Div(String.valueOf(c));
    private BiFunction<C, I, Component> itemRenderer = (c, i) -> new com.vaadin.flow.component.html.Div(String.valueOf(i));

    private int categoryWindow = Integer.MAX_VALUE / 4;
    private int itemWindow = Integer.MAX_VALUE / 4;
    private double anchorX = 0.3;
    private double anchorY = 0.3;
    private double scale = 1.0;

    public XMediaBar() {
        this(new XmbNavigator<>());
    }

    private XMediaBar(XmbNavigator<C, I> navigator) {
        super(new XmbLayout<>(navigator));
        this.navigator = navigator;
        this.xmb = (XmbLayout<C, I>) getLayout();
        getElement().getClassList().add("xmb");
        getElement().setAttribute("role", "application");
        setAnimateItems(true);
        setDraggingEnabled(false);
        setZoomingEnabled(false);
    }

    // ── Configuration ────────────────────────────────────────────────────

    public XmbLayout<C, I> getXmbLayout() { return xmb; }

    public XmbNavigator<C, I> getNavigator() { return navigator; }

    public XMediaBar<C, I> setCategories(List<C> categories) {
        this.categories = List.copyOf(categories);
        return this;
    }

    public XMediaBar<C, I> setItemProvider(Function<C, List<I>> itemsOf) {
        this.itemsOf = itemsOf;
        return this;
    }

    public XMediaBar<C, I> setFocusable(Predicate<I> focusable) {
        this.focusable = focusable;
        return this;
    }

    public XMediaBar<C, I> setCategoryRenderer(ComponentProvider<C> renderer) {
        this.categoryRenderer = renderer;
        return this;
    }

    public XMediaBar<C, I> setItemRenderer(BiFunction<C, I, Component> renderer) {
        this.itemRenderer = renderer;
        return this;
    }

    /** Only categories within this distance of the active one, and items within this distance of each focused item, are rendered. */
    public XMediaBar<C, I> setRenderWindow(int categories, int items) {
        this.categoryWindow = categories;
        this.itemWindow = items;
        return this;
    }

    /** Viewport fraction (0..1) where the active category header is anchored. */
    public XMediaBar<C, I> setAnchor(double fx, double fy) {
        this.anchorX = fx;
        this.anchorY = fy;
        return this;
    }

    public XMediaBar<C, I> setScale(double scale) {
        this.scale = scale;
        return this;
    }

    /** Re-read the model (categories, items, focusable), reconcile focus, and re-render. */
    public XMediaBar<C, I> refresh() {
        navigator.setModel(categories, itemsOf, focusable);
        sync();
        return this;
    }

    // ── State ────────────────────────────────────────────────────────────

    public Optional<C> getActiveCategory() { return navigator.activeCategory(); }

    public Optional<I> getFocusedItem() { return navigator.focusedItem(); }

    // ── Commands ─────────────────────────────────────────────────────────

    /** Entry point for every input source: keyboard, pointer, gamepad, or application code. */
    public void execute(NavigationCommand command) {
        apply(navigator.execute(command));
    }

    public void selectCategory(C category) {
        apply(navigator.selectCategory(category));
    }

    public void focusItem(C category, I item) {
        apply(navigator.focusItem(category, item));
    }

    private void apply(XmbNavigator.Result<C, I> result) {
        if (result.categoryChanged() || result.focusChanged()) {
            sync();
        }
        if (result.categoryChanged()) {
            navigator.activeCategory().ifPresent(c -> fireEvent(new CategorySelectedEvent<>(this, c)));
        }
        if (result.focusChanged()) {
            navigator.activeCategory().ifPresent(c ->
                    fireEvent(new ItemFocusedEvent<>(this, c, navigator.focusedItem(c).orElse(null))));
        }
        result.activated().ifPresent(i ->
                navigator.activeCategory().ifPresent(c -> fireEvent(new ItemActivatedEvent<>(this, c, i))));
        if (result.back()) {
            fireEvent(new BackEvent<>(this));
        }
    }

    /** Binds arrows, Enter and Escape on the scope. Ignored while typing in a field. */
    public Registration bindKeys(Component scope) {
        return KeyBindings.bind(scope, List.copyOf(KEYS.keySet()), key -> execute(KEYS.get(key)));
    }

    // ── Rendering ────────────────────────────────────────────────────────

    /** Bring the DOM, item positions, state classes and viewport in line with the navigator. */
    private void sync() {
        List<C> cats = navigator.categories();
        int activeIdx = navigator.activeIndex();
        C active = navigator.activeCategory().orElse(null);

        Set<XmbKey> wanted = new LinkedHashSet<>();
        for (int ci = 0; ci < cats.size(); ci++) {
            C c = cats.get(ci);
            if (Math.abs(ci - activeIdx) > categoryWindow) continue;
            wanted.add(new XmbKey.Category<>(c));
            List<I> list = navigator.itemsOf(c);
            int f = Math.max(0, navigator.focusedIndex(c));
            for (int j = 0; j < list.size(); j++) {
                if (Math.abs(j - f) > itemWindow) continue;
                wanted.add(new XmbKey.Item<>(c, list.get(j)));
            }
        }

        for (XmbKey key : new ArrayList<>(keys())) {
            if (!wanted.contains(key)) removeItem(key);
        }
        for (XmbKey key : wanted) {
            if (!hasItem(key)) materialise(key);
        }

        relayout(wanted);

        for (XmbKey key : wanted) {
            itemElement(key).ifPresent(el -> tag(el, key, active));
        }

        if (active != null) {
            Rect header = xmb.bounds(new XmbKey.Category<>(active));
            anchorPoint(header.centerX(), header.centerY(), anchorX, anchorY, scale);
        }
    }

    @SuppressWarnings("unchecked")
    private void materialise(XmbKey key) {
        if (key instanceof XmbKey.Category<?> cat) {
            C c = (C) cat.id();
            addItem(key, categoryRenderer.create(c));
            itemElement(key).ifPresent(el -> {
                el.getClassList().add("xmb-category");
                el.setAttribute("role", "tab");
                el.addEventListener("click", e -> selectCategory(c));
            });
        } else {
            XmbKey.Item<?, ?> it = (XmbKey.Item<?, ?>) key;
            C c = (C) it.category();
            I i = (I) it.id();
            addItem(key, itemRenderer.apply(c, i));
            itemElement(key).ifPresent(el -> {
                el.getClassList().add("xmb-item");
                el.setAttribute("role", "option");
                el.addEventListener("click", e -> {
                    boolean isFocused = c.equals(navigator.activeCategory().orElse(null))
                            && i.equals(navigator.focusedItem(c).orElse(null));
                    if (isFocused) execute(NavigationCommand.ACTIVATE);
                    else focusItem(c, i);
                });
            });
        }
    }

    @SuppressWarnings("unchecked")
    private void tag(Element el, XmbKey key, C active) {
        if (key instanceof XmbKey.Category<?> cat) {
            boolean isActive = cat.id().equals(active);
            el.getClassList().set("xmb-active", isActive);
            el.setAttribute("aria-selected", String.valueOf(isActive));
        } else {
            XmbKey.Item<?, ?> it = (XmbKey.Item<?, ?>) key;
            C c = (C) it.category();
            I i = (I) it.id();
            boolean inActive = c.equals(active);
            boolean isFocused = inActive && i.equals(navigator.focusedItem(c).orElse(null));
            el.getClassList().set("xmb-collapsed", !inActive);
            el.getClassList().set("xmb-focused", isFocused);
            el.getClassList().set("xmb-disabled", !navigator.isFocusable(i));
            el.setAttribute("aria-selected", String.valueOf(isFocused));
            if (isFocused) el.setAttribute("aria-current", "true");
            else el.removeAttribute("aria-current");
        }
    }

    // ── Events ───────────────────────────────────────────────────────────

    public static class CategorySelectedEvent<C, I> extends ComponentEvent<XMediaBar<C, I>> {
        private final C category;
        public CategorySelectedEvent(XMediaBar<C, I> source, C category) {
            super(source, false);
            this.category = category;
        }
        public C getCategory() { return category; }
    }

    public static class ItemFocusedEvent<C, I> extends ComponentEvent<XMediaBar<C, I>> {
        private final C category;
        private final I item;
        public ItemFocusedEvent(XMediaBar<C, I> source, C category, I item) {
            super(source, false);
            this.category = category;
            this.item = item;
        }
        public C getCategory() { return category; }
        /** Null when the active category has no focusable item. */
        public I getItem() { return item; }
    }

    public static class ItemActivatedEvent<C, I> extends ComponentEvent<XMediaBar<C, I>> {
        private final C category;
        private final I item;
        public ItemActivatedEvent(XMediaBar<C, I> source, C category, I item) {
            super(source, false);
            this.category = category;
            this.item = item;
        }
        public C getCategory() { return category; }
        public I getItem() { return item; }
    }

    public static class BackEvent<C, I> extends ComponentEvent<XMediaBar<C, I>> {
        public BackEvent(XMediaBar<C, I> source) { super(source, false); }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Registration addCategorySelectedListener(ComponentEventListener<CategorySelectedEvent<C, I>> l) {
        return addListener((Class) CategorySelectedEvent.class, (ComponentEventListener) l);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Registration addItemFocusedListener(ComponentEventListener<ItemFocusedEvent<C, I>> l) {
        return addListener((Class) ItemFocusedEvent.class, (ComponentEventListener) l);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Registration addItemActivatedListener(ComponentEventListener<ItemActivatedEvent<C, I>> l) {
        return addListener((Class) ItemActivatedEvent.class, (ComponentEventListener) l);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Registration addBackListener(ComponentEventListener<BackEvent<C, I>> l) {
        return addListener((Class) BackEvent.class, (ComponentEventListener) l);
    }
}
