package io.github.adumeige.vaadin.workspaces.xmb;

import io.github.adumeige.vaadin.workspaces.Rect;
import io.github.adumeige.vaadin.workspaces.WorkspaceLayout;

/**
 * XMB geometry. Categories form a horizontal row at y = 0. The active category's items hang
 * in a column under it, positioned relative to the focused index so the focused item always
 * sits just below the row; items before it are placed above the row. Items of inactive
 * categories collapse behind their header.
 *
 * Bounds depend on navigation state, which is read from the navigator the layout is given.
 */
public final class XmbLayout<C, I> implements WorkspaceLayout<XmbKey> {

    private final XmbNavigator<C, I> navigator;
    private double categoryWidth = 160;
    private double categoryHeight = 120;
    private double categoryGap = 40;
    private double itemWidth = 160;
    private double itemHeight = 72;
    private double itemGap = 12;
    private double headerGap = 24;
    private ItemAlignment itemAlignment = ItemAlignment.CENTER;

    /** Horizontal placement of an item column relative to its category header. */
    public enum ItemAlignment { START, CENTER, END }

    public XmbLayout(XmbNavigator<C, I> navigator) {
        this.navigator = navigator;
    }

    public XmbLayout<C, I> categorySize(double width, double height) {
        this.categoryWidth = width;
        this.categoryHeight = height;
        return this;
    }

    public XmbLayout<C, I> categoryGap(double gap) { this.categoryGap = gap; return this; }

    public XmbLayout<C, I> itemSize(double width, double height) {
        this.itemWidth = width;
        this.itemHeight = height;
        return this;
    }

    public XmbLayout<C, I> itemGap(double gap) { this.itemGap = gap; return this; }

    public XmbLayout<C, I> headerGap(double gap) { this.headerGap = gap; return this; }

    /** How item columns line up with their header; {@link ItemAlignment#CENTER} by default. */
    public XmbLayout<C, I> itemAlignment(ItemAlignment alignment) { this.itemAlignment = alignment; return this; }

    public double getCategoryWidth()  { return categoryWidth; }
    public double getCategoryHeight() { return categoryHeight; }
    public double getItemHeight()     { return itemHeight; }
    public double getItemGap()        { return itemGap; }

    public double categoryX(int categoryIndex) {
        return categoryIndex * (categoryWidth + categoryGap);
    }

    private double itemOffset() {
        switch (itemAlignment) {
            case START: return 0;
            case END: return categoryWidth - itemWidth;
            default: return (categoryWidth - itemWidth) / 2;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Rect bounds(XmbKey key) {
        if (key instanceof XmbKey.Category<?> cat) {
            int idx = navigator.categories().indexOf((C) cat.id());
            return new Rect(categoryX(idx), 0, categoryWidth, categoryHeight);
        }
        XmbKey.Item<?, ?> it = (XmbKey.Item<?, ?>) key;
        C c = (C) it.category();
        I i = (I) it.id();
        int cIdx = navigator.categories().indexOf(c);
        double x = categoryX(cIdx) + itemOffset();
        boolean active = navigator.activeCategory().map(c::equals).orElse(false);
        if (!active) {
            return new Rect(x, categoryHeight + headerGap, itemWidth, itemHeight);
        }
        int j = navigator.itemsOf(c).indexOf(i);
        int f = Math.max(0, navigator.focusedIndex(c));
        double stride = itemHeight + itemGap;
        double y = j >= f
                ? categoryHeight + headerGap + (j - f) * stride
                : (j - f) * stride - headerGap;
        return new Rect(x, y, itemWidth, itemHeight);
    }
}
