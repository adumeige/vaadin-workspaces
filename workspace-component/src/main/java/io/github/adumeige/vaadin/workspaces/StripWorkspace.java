package io.github.adumeige.vaadin.workspaces;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.shared.Registration;
import io.github.adumeige.vaadin.workspaces.layout.LinearLayout;
import io.github.adumeige.vaadin.workspaces.layout.LinearLayout.Orientation;

import java.util.ArrayList;
import java.util.List;

/**
 * A leaf: {@link Workspace} + {@link LinearLayout} + sequential navigation.
 * Items form a strip along one axis; only the two arrow keys along that axis are bound.
 */
public class StripWorkspace extends Workspace<Integer> {

    private final LinearLayout strip;
    private final List<Double> lengths = new ArrayList<>();
    private final double defaultLength;
    private int current = 0;

    public StripWorkspace(Orientation orientation, double itemLength, double crossSize, double gap) {
        super(new LinearLayout(orientation, itemLength, crossSize, gap));
        this.strip = (LinearLayout) getLayout();
        this.defaultLength = itemLength;
        strip.setLengthOf(i -> i < lengths.size() ? lengths.get(i) : defaultLength);
        addViewChangedListener(e -> {
            ViewState v = e.getView();
            Geometry.nearest(v.centerX(), v.centerY(), keys(), strip::bounds).ifPresent(i -> {
                current = i;
                fireEvent(new ItemFocusedEvent(this, e.isFromClient(), i));
            });
        });
    }

    /** Append an item with the default length along the axis. */
    public int append(Component component) {
        return append(component, defaultLength);
    }

    /** Append an item with its own length along the axis. */
    public int append(Component component, double length) {
        int index = lengths.size();
        lengths.add(length);
        addItem(index, component);
        return index;
    }

    public int getCurrentIndex() { return current; }

    public void goTo(int index) {
        if (index < 0 || index >= itemCount()) return;
        current = index;
        Rect r = strip.bounds(index);
        centerOn(r.centerX(), r.centerY());
    }

    public void next()     { goTo(current + 1); }
    public void previous() { goTo(current - 1); }
    public void first()    { goTo(0); }
    public void last()     { goTo(itemCount() - 1); }

    /** Binds the two arrow keys along the strip axis, plus Home and End. */
    public Registration bindArrowKeys(Component scope) {
        boolean horizontal = strip.getOrientation() == Orientation.HORIZONTAL;
        String prev = horizontal ? "ArrowLeft" : "ArrowUp";
        String nxt = horizontal ? "ArrowRight" : "ArrowDown";
        return KeyBindings.bind(scope, List.of(prev, nxt, "Home", "End"), key -> {
            if (key.equals(prev)) previous();
            else if (key.equals(nxt)) next();
            else if (key.equals("Home")) first();
            else last();
        });
    }

    public static class ItemFocusedEvent extends ComponentEvent<StripWorkspace> {
        private final int index;

        public ItemFocusedEvent(StripWorkspace source, boolean fromClient, int index) {
            super(source, fromClient);
            this.index = index;
        }

        public int getIndex() { return index; }
    }

    public Registration addItemFocusedListener(ComponentEventListener<ItemFocusedEvent> listener) {
        return addListener(ItemFocusedEvent.class, listener);
    }
}
