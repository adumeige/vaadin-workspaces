package io.github.adumeige.vaadin.workspaces.xmb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The XMB navigation state machine. Pure Java, no UI: holds the ordered categories, the
 * active category, and one focused item per category. Every command resolves synchronously
 * to a deterministic new state, so rapid input never depends on animation timing.
 *
 * @param <C> category identifier
 * @param <I> item identifier
 */
public final class XmbNavigator<C, I> {

    /** What a command changed, for the UI to react to. */
    public record Result<C, I>(boolean categoryChanged, boolean focusChanged, Optional<I> activated, boolean back) {
        static <C, I> Result<C, I> none() { return new Result<>(false, false, Optional.empty(), false); }
    }

    private List<C> categories = List.of();
    private final Map<C, List<I>> items = new LinkedHashMap<>();
    private Predicate<I> focusable = i -> true;

    private C active;
    private final Map<C, I> focused = new HashMap<>();

    // ── Model ────────────────────────────────────────────────────────────

    /**
     * Replace the model. Focus is reconciled by identity first, then by nearest index,
     * so additions, removals and reorders never leave stale references.
     */
    public void setModel(List<C> newCategories, Function<C, List<I>> itemsOf, Predicate<I> focusablePredicate) {
        int oldActiveIndex = activeIndex();
        Map<C, Integer> oldFocusedIndex = new HashMap<>();
        for (Map.Entry<C, I> e : focused.entrySet()) {
            List<I> old = items.getOrDefault(e.getKey(), List.of());
            oldFocusedIndex.put(e.getKey(), old.indexOf(e.getValue()));
        }

        categories = List.copyOf(newCategories);
        items.clear();
        for (C c : categories) {
            items.put(c, List.copyOf(itemsOf.apply(c)));
        }
        focusable = focusablePredicate == null ? i -> true : focusablePredicate;

        // Active category: same identity, else nearest index, else none.
        if (active == null || !items.containsKey(active)) {
            active = categories.isEmpty() ? null
                    : categories.get(Math.max(0, Math.min(categories.size() - 1, Math.max(oldActiveIndex, 0))));
        }

        // Per-category focus: keep by identity, else nearest focusable by old index, else default.
        focused.keySet().retainAll(items.keySet());
        for (C c : categories) {
            List<I> list = items.get(c);
            I current = focused.get(c);
            if (current != null && list.contains(current) && focusable.test(current)) continue;
            int hint = oldFocusedIndex.getOrDefault(c, 0);
            Optional<I> next = nearestFocusable(list, hint);
            if (next.isPresent()) focused.put(c, next.get());
            else focused.remove(c);
        }
    }

    public List<C> categories() { return categories; }

    public List<I> itemsOf(C category) { return items.getOrDefault(category, List.of()); }

    public boolean isFocusable(I item) { return focusable.test(item); }

    // ── State ────────────────────────────────────────────────────────────

    public Optional<C> activeCategory() { return Optional.ofNullable(active); }

    public int activeIndex() { return active == null ? -1 : categories.indexOf(active); }

    public Optional<I> focusedItem() { return active == null ? Optional.empty() : focusedItem(active); }

    public Optional<I> focusedItem(C category) { return Optional.ofNullable(focused.get(category)); }

    public int focusedIndex(C category) {
        I f = focused.get(category);
        return f == null ? -1 : itemsOf(category).indexOf(f);
    }

    /** Immutable view of the full state, for tests and diagnostics. */
    public Map<C, I> focusedByCategory() { return Collections.unmodifiableMap(new HashMap<>(focused)); }

    // ── Commands ─────────────────────────────────────────────────────────

    public Result<C, I> execute(NavigationCommand command) {
        return switch (command) {
            case PREVIOUS_CATEGORY -> stepCategory(-1);
            case NEXT_CATEGORY -> stepCategory(+1);
            case PREVIOUS_ITEM -> stepItem(-1);
            case NEXT_ITEM -> stepItem(+1);
            case ACTIVATE -> new Result<>(false, false, focusedItem(), false);
            case BACK -> new Result<>(false, false, Optional.empty(), true);
        };
    }

    /** Make a category active directly, as a pointer click would. */
    public Result<C, I> selectCategory(C category) {
        if (!items.containsKey(category) || category.equals(active)) return Result.none();
        active = category;
        ensureDefaultFocus(category);
        return new Result<>(true, true, Optional.empty(), false);
    }

    /** Focus an item directly, selecting its category if needed, as a pointer click would. */
    public Result<C, I> focusItem(C category, I item) {
        if (!itemsOf(category).contains(item) || !focusable.test(item)) return Result.none();
        boolean categoryChanged = !category.equals(active);
        boolean focusChanged = !item.equals(focused.get(category));
        active = category;
        focused.put(category, item);
        return new Result<>(categoryChanged, focusChanged || categoryChanged, Optional.empty(), false);
    }

    private Result<C, I> stepCategory(int delta) {
        if (categories.isEmpty()) return Result.none();
        int idx = activeIndex();
        int target = Math.max(0, Math.min(categories.size() - 1, idx + delta));
        if (target == idx) return Result.none();
        active = categories.get(target);
        ensureDefaultFocus(active);
        return new Result<>(true, true, Optional.empty(), false);
    }

    private Result<C, I> stepItem(int delta) {
        if (active == null) return Result.none();
        List<I> list = itemsOf(active);
        if (list.isEmpty()) return Result.none();
        int idx = focusedIndex(active);
        if (idx < 0) {
            Optional<I> first = nearestFocusable(list, 0);
            if (first.isEmpty()) return Result.none();
            focused.put(active, first.get());
            return new Result<>(false, true, Optional.empty(), false);
        }
        for (int i = idx + delta; i >= 0 && i < list.size(); i += delta) {
            if (focusable.test(list.get(i))) {
                focused.put(active, list.get(i));
                return new Result<>(false, true, Optional.empty(), false);
            }
        }
        return Result.none();
    }

    private void ensureDefaultFocus(C category) {
        if (focused.containsKey(category)) return;
        nearestFocusable(itemsOf(category), 0).ifPresent(i -> focused.put(category, i));
    }

    /** The focusable item nearest to the index hint, searching outward in both directions. */
    private Optional<I> nearestFocusable(List<I> list, int hint) {
        if (list.isEmpty()) return Optional.empty();
        int start = Math.max(0, Math.min(list.size() - 1, hint));
        for (int d = 0; d < list.size(); d++) {
            int lo = start - d, hi = start + d;
            if (lo >= 0 && focusable.test(list.get(lo))) return Optional.of(list.get(lo));
            if (hi < list.size() && focusable.test(list.get(hi))) return Optional.of(list.get(hi));
        }
        return Optional.empty();
    }

    List<C> categoriesCopy() { return new ArrayList<>(categories); }
}
