package io.github.adumeige.vaadin.workspaces.xmb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.github.adumeige.vaadin.workspaces.xmb.NavigationCommand.*;
import static org.junit.jupiter.api.Assertions.*;

class XmbNavigatorTest {

    private XmbNavigator<String, String> nav;
    private Map<String, List<String>> model;

    @BeforeEach
    void setUp() {
        nav = new XmbNavigator<>();
        model = Map.of(
                "music", List.of("m1", "m2", "m3"),
                "video", List.of(),
                "games", List.of("g1", "g2-disabled", "g3"));
        nav.setModel(List.of("music", "video", "games"), model::get, i -> !i.endsWith("disabled"));
    }

    @Test
    void initialStateIsFirstCategoryFirstItem() {
        assertEquals(Optional.of("music"), nav.activeCategory());
        assertEquals(Optional.of("m1"), nav.focusedItem());
    }

    @Test
    void verticalNavigationStopsAtBoundaries() {
        assertTrue(nav.execute(NEXT_ITEM).focusChanged());
        assertTrue(nav.execute(NEXT_ITEM).focusChanged());
        assertEquals(Optional.of("m3"), nav.focusedItem());
        assertFalse(nav.execute(NEXT_ITEM).focusChanged());
        assertEquals(Optional.of("m3"), nav.focusedItem());
        assertFalse(nav.execute(PREVIOUS_ITEM).categoryChanged());
    }

    @Test
    void horizontalNavigationStopsAtBoundariesAndRemembersFocus() {
        nav.execute(NEXT_ITEM);
        assertEquals(Optional.of("m2"), nav.focusedItem());
        assertTrue(nav.execute(NEXT_CATEGORY).categoryChanged());
        assertEquals(Optional.of("video"), nav.activeCategory());
        assertTrue(nav.execute(NEXT_CATEGORY).categoryChanged());
        assertFalse(nav.execute(NEXT_CATEGORY).categoryChanged());
        assertEquals(Optional.of("games"), nav.activeCategory());
        nav.execute(PREVIOUS_CATEGORY);
        nav.execute(PREVIOUS_CATEGORY);
        assertEquals(Optional.of("m2"), nav.focusedItem(), "music must restore its focused item");
    }

    @Test
    void emptyCategoryHasNoFocusAndToleratesItemCommands() {
        nav.execute(NEXT_CATEGORY);
        assertEquals(Optional.of("video"), nav.activeCategory());
        assertTrue(nav.focusedItem().isEmpty());
        assertFalse(nav.execute(NEXT_ITEM).focusChanged());
        assertTrue(nav.execute(ACTIVATE).activated().isEmpty());
    }

    @Test
    void disabledItemsAreSkipped() {
        nav.selectCategory("games");
        assertEquals(Optional.of("g1"), nav.focusedItem());
        nav.execute(NEXT_ITEM);
        assertEquals(Optional.of("g3"), nav.focusedItem());
        nav.execute(PREVIOUS_ITEM);
        assertEquals(Optional.of("g1"), nav.focusedItem());
        assertFalse(nav.focusItem("games", "g2-disabled").focusChanged());
    }

    @Test
    void activateAndBackAreReportedNotInterpreted() {
        XmbNavigator.Result<String, String> r = nav.execute(ACTIVATE);
        assertEquals(Optional.of("m1"), r.activated());
        assertFalse(r.focusChanged());
        assertTrue(nav.execute(BACK).back());
        assertEquals(Optional.of("music"), nav.activeCategory(), "back must not change category");
    }

    @Test
    void rapidInputIsDeterministic() {
        for (int i = 0; i < 50; i++) nav.execute(i % 2 == 0 ? NEXT_ITEM : NEXT_CATEGORY);
        assertEquals(Optional.of("games"), nav.activeCategory());
        assertEquals(Optional.of("g3"), nav.focusedItem());
    }

    @Test
    void modelChangeKeepsFocusByIdentity() {
        nav.execute(NEXT_ITEM);
        model = Map.of("music", List.of("m0", "m2", "m1"), "games", List.of("g3"));
        nav.setModel(List.of("games", "music"), model::get, i -> true);
        assertEquals(Optional.of("music"), nav.activeCategory());
        assertEquals(Optional.of("m2"), nav.focusedItem());
        assertEquals(Optional.of("g3"), nav.focusedItem("games"));
    }

    @Test
    void modelChangeFallsBackToNearestIndexWhenItemRemoved() {
        nav.execute(NEXT_ITEM);
        nav.execute(NEXT_ITEM); // m3, index 2
        model = Map.of("music", List.of("m1", "m2"));
        nav.setModel(List.of("music"), model::get, i -> true);
        assertEquals(Optional.of("m2"), nav.focusedItem());
    }

    @Test
    void modelChangeFallsBackToNearestCategoryWhenActiveRemoved() {
        nav.execute(NEXT_CATEGORY); // video, index 1
        model = Map.of("music", List.of("m1"), "games", List.of("g1"));
        nav.setModel(List.of("music", "games"), model::get, i -> true);
        assertEquals(Optional.of("games"), nav.activeCategory());
        assertEquals(Optional.of("g1"), nav.focusedItem());
    }

    @Test
    void emptyModelHasNoActiveCategory() {
        nav.setModel(List.of(), c -> List.of(), i -> true);
        assertTrue(nav.activeCategory().isEmpty());
        assertFalse(nav.execute(NEXT_CATEGORY).categoryChanged());
        assertFalse(nav.execute(NEXT_ITEM).focusChanged());
    }

    @Test
    void focusedByCategoryNeverReferencesUnknownItems() {
        nav.focusedByCategory().forEach((c, i) -> assertTrue(Set.copyOf(model.get(c)).contains(i)));
    }
}
