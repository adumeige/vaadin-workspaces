# vaadin-workspaces

Flat-surface workspaces for Vaadin Flow: items sit at fixed positions on an infinite canvas, and a
pannable, zoomable viewport moves over them. One generic client element, many layouts, all written in Java.

## Modules

- `workspace-component` — the `Workspace` base, layouts, and leaf components (Java, with one TypeScript element).
- `workspace-doodles` — an annotation layer (circles, highlights, arrows, pen, marker, text, notes) for any workspace.
- `workspace-component-karibu`, `workspace-doodles-karibu` — [karibu-dsl](https://github.com/mvysny/karibu-dsl) builders for Kotlin.
- `workspace-demo` — a Spring Boot + Kotlin demo written with the karibu builders, one route per leaf.

## Requirements

- Java 17
- Maven
- Vaadin 24.10, Spring Boot 3.5

## Architecture

```
workspace-viewport.ts      client: positions [data-item] children from data-x/y/w/h, pans, zooms, animates,
                           reports where the viewport settled. Knows nothing about layouts or keys.
Workspace<K>               server container: items keyed by K, asks a WorkspaceLayout<K> for bounds,
                           drives the viewport (fitTo, zoomTo, fitAll, setView, anchorPoint, centerOn).
                           No navigation semantics.
WorkspaceLayout<K>         pure geometry: Rect bounds(K). GridLayout, LinearLayout, FreeLayout, XmbLayout.
Leaves                     Workspace + layout + component provider + behaviours (keys, current item, events):
                           GridWorkspace, StripWorkspace, FreeWorkspace<K>, XMediaBar<C, I>, CascadeWorkspace.
```

Keyboard navigation is resolved on the server by the leaf, because "next" means something different
on a grid, a strip, a free canvas or a cross media bar. The only client-side input logic is a guard
that ignores keys typed into a field inside an item.

## Leaves

### GridWorkspace

![Grid workspace: six cells fitted in the viewport](docs/grid.png)

```java
GridWorkspace workspace = new GridWorkspace(800, 600); // cell width, height in px
workspace.setSize("100%", "100%");
workspace.setCellMargin(12);
workspace.addCell(0, 0, new Span("First"));
workspace.addCell(1, 0, new Span("Second"));
workspace.setInitialFitAll();
workspace.bindArrowKeys(layout);              // arrows pan by one cell, clamped to the occupied extent
workspace.addCellFocusedListener(e -> System.out.println(e.getCol() + "," + e.getRow()));
```

| Method | Effect |
| --- | --- |
| `fitAll()` | Show every cell |
| `fitToCell(col, row)` | Move to a cell |
| `zoomToCell(col, row)` | Zoom out, then in on a cell |
| `moveLeft()` / `moveRight()` / `moveUp()` / `moveDown()` | Pan by one cell at the current scale |
| `setDraggingEnabled(boolean)` / `setZoomingEnabled(boolean)` | Toggle mouse drag and zoom |

### StripWorkspace

![Strip workspace: items of varying widths along one axis](docs/strip.png)

A sequence along one axis, with per-item lengths. `append(component[, length])`, `next()`, `previous()`,
`first()`, `last()`, `goTo(index)`, `bindArrowKeys(scope)` binds the two arrows along the axis plus Home and End.

### FreeWorkspace<K>

![Free workspace: caller-placed cards, arrow keys jump to the nearest neighbour](docs/free.png)

Caller-supplied rectangles. `place(key, x, y, w, h, component)`, `goTo(key)`, `move(Direction)` picks the
nearest item in that direction through `Geometry.neighbour`.

### XMediaBar<C, I>

![XMediaBar: categories across, the active category's items down, focused item under its header](docs/xmb.png)

A PlayStation-style cross media bar. Categories run across; the active category's items run down.

```java
XMediaBar<Category, Item> bar = new XMediaBar<>();
bar.setCategories(categories);
bar.setItemProvider(c -> itemsOf(c));
bar.setFocusable(Item::isEnabled);
bar.setCategoryRenderer(c -> new Div(c.label()));
bar.setItemRenderer((c, i) -> new Div(i.label()));
bar.setRenderWindow(2, 8);                    // bounded rendering: categories ±2, items ±8
bar.addItemActivatedListener(e -> open(e.getItem()));
bar.refresh();                                // reads the model, reconciles focus, renders
bar.bindKeys(layout);                         // arrows, Enter, Escape
```

- Every input goes through `execute(NavigationCommand)`: `PREVIOUS_CATEGORY`, `NEXT_CATEGORY`, `PREVIOUS_ITEM`,
  `NEXT_ITEM`, `ACTIVATE`, `BACK`. Clicks resolve to `selectCategory` / `focusItem` / `ACTIVATE`.
- State lives in `XmbNavigator`, a pure Java state machine. Commands resolve synchronously, so rapid input is
  deterministic and never depends on animation timing. Each category remembers its focused item. Boundaries
  stop, nothing wraps, empty categories have no focus, non-focusable items are skipped.
- Call `refresh()` after changing the model. Focus is reconciled by identity, then nearest index.
- Horizontal moves are viewport moves (the active header is anchored at `setAnchor(fx, fy)`). Vertical moves are
  relayouts of the active column, animated on the client with the same duration, so both axes transition together.
- Geometry is `getXmbLayout()`: `categorySize`, `categoryGap`, `itemSize`, `itemGap`, `headerGap`, and
  `itemAlignment(START | CENTER | END)` for where an item column sits under its header.
- Emphasis is CSS: `xmb-category`, `xmb-active`, `xmb-item`, `xmb-focused`, `xmb-collapsed`, `xmb-disabled`.
  Override `--ws-scale` and `opacity` on those classes to restyle. Reduced motion collapses all durations.
- Events: `CategorySelected`, `ItemFocused`, `ItemActivated`, `Back`. Activation and back do nothing by themselves.

### CascadeWorkspace

![CascadeWorkspace: cases column active, selected case aligned with the documents derived from it](docs/cascade.png)

Any number of sliding columns side by side. Each column has one selection, which always sits on a
shared anchor row, so selections are aligned across columns and changing one slides its column. A
column can be derived from another's selection, so choices cascade. Inspired by Miller columns, but
columns keep their selection on the anchor row instead of scrolling independently.

```java
CascadeWorkspace ws = new CascadeWorkspace();
SlidingColumn<Slot> slots = ws.addColumn(slot -> myDropCard(files -> ws.open(cases, new Case(files))));
SlidingColumn<Case> cases = ws.addColumn(c -> new Div(c.name()));
SlidingColumn<Doc>  docs  = ws.addColumn(d -> new Div(d.name()));
SlidingColumn<Note> notes = ws.addColumn(n -> new Div(n.text()));
ws.derive(cases, docs, c -> docsOf(c));     // docs follow the selected case
ws.derive(cases, notes, c -> notesOf(c));
slots.setItems(List.of(Slot.DROP));
ws.refresh();
ws.open(cases, existingCase);               // add if new, select, activate the column
ws.bindKeys(layout);                        // Left/Right: column, Up/Down: selection, Enter: activate
```

- `addColumn(renderer)` returns the typed `SlidingColumn<T>` handle: a pure Java list with one selection
  and listeners (`select`, `next`, `setItems`, `add`, `remove`). Handles are how the application fills,
  wires and refers to columns; `column(i)`, `indexOf(column)` and `columnCount()` bridge to indices.
- `derive(from, to, fn)` keeps `to` filled with `fn(selected of from)`, empty when `from` has no selection.
  Any column can derive from any other; selection survives content changes by identity, then nearest index.
- The viewport fits a band of `setVisibleRows(n)` rows across all columns (`ViewMode.ALL_COLUMNS`) or the
  active column only (`ViewMode.ACTIVE_COLUMN`), using the base `fitRect(Rect, padding)`. `ViewMode.MANUAL`
  leaves the viewport to the application, which can react to the events and call `fitRect`, `centerOn` or
  `fitTo` itself; `getLayout().band(from, to, rows)` gives the anchor-row rectangle across columns.
- Events, each carrying the column index: `ColumnActivated`, `SelectionChanged`, `Slide`, `ContentChanged`,
  `ItemActivated`, `ItemOpened`.
- Classes: `cascade-item`, `cascade-col-N`, `cascade-selected`, `cascade-active-col`. Styles in `cascade.css`.

## Doodles

![Doodles: programmatic arrow and note, user-drawn circle and marker, sketchy render mode on](docs/doodles.png)

`workspace-doodles` adds a `DoodleLayer<K>` above or below any workspace's items. The layer lives in the
viewport's transformed canvas, so doodles pan and zoom with the surface. Outlines keep a constant pixel
width under zoom; markers are in surface units so they keep covering the same content.

```java
DoodleLayer<GridCell> layer = new DoodleLayer<>(grid, Placement.ABOVE);
layer.add(Doodle.arrow(Anchor.on(new GridCell(0, 0), 1, 0.5), Anchor.on(new GridCell(1, 0), 0, 0.5)));
layer.add(Doodle.ellipse(Anchor.at(300, 200), 200, 120).withStyle(DoodleStyle.DEFAULT.withColor("#3182ce")));
layer.setTool(DoodleTool.PEN);                 // user draws; shapes come back as DoodleAddedEvent
layer.addDoodleAddedListener(e -> save(e.getDoodle()));
```

- Doodles: `Ellipse`, `HighlightRect`, `Arrow`, `Stroke` (pen), `Marker`, `TextHighlight`, `Text`, `Note`.
- Anchors: `Anchor.at(x, y)` is absolute; `Anchor.on(key, fx, fy)` is a fraction of an item's bounds and
  follows the item when the workspace relayouts (the base notifies layers through `WorkspaceLayer`).
  User-drawn points that start on an item are anchored to it unless `setAnchorToItems(false)`.
- Tools (`setTool`): `ELLIPSE`, `RECT`, `ARROW`, `PEN`, `MARKER`, `TEXT_MARKER` (select text inside an item
  to highlight it), `TEXT`, `NOTE` (inline editor, Enter or Ctrl+Enter commits), `ERASER`, `NONE`
  (clicks on doodles fire `DoodleClickedEvent`). Escape returns to `NONE`. Dragging the surface is
  suspended while a tool is active.
- `undo()`, `remove(id)`, `clear()`, `doodles()`, `setDrawStyle(DoodleStyle)` for the next drawn shape.
- Styling hooks: `doodles.css` variables and `data-doodle-type` on every shape.
- Hand-drawn look: `layer.setRenderMode(RenderMode.SKETCHY)` for the whole layer, or per doodle with
  `style.withRender(RenderMode.SKETCHY)` (`DEFAULT` follows the layer; `restyle(id, style)` changes one in
  place). Strokes are drawn twice with seeded jitter, highlights become hachures, text uses
  `--doodle-sketch-font`. The technique is a compact take on the one popularised by rough.js (MIT).

Base hooks used by the layer, available to any other layer: `Workspace.addLayer(component, placement)`,
`itemId(key)` / `itemKey(id)` (items carry `data-item-id`), `resolve(key, fx, fy)`, `bounds(key)`, and
`WorkspaceLayer.onRelayout`.

## Kotlin: karibu-dsl builders

Two small modules make the workspaces and doodles first-class citizens of a karibu-dsl layout. They add
no behaviour, only builders, so the Java API stays the single source of truth.

```kotlin
import io.github.adumeige.vaadin.workspaces.karibu.*
import io.github.adumeige.vaadin.workspaces.doodles.karibu.*

verticalLayout {
    gridWorkspace(600, 400) {
        setCellMargin(40)
        cell(0, 0) { card { header = H3("First"); p("…") } }
        cell(1, 0) { card { header = H3("Second") } }
        doodleLayer {
            arrow(on(GridCell(0, 0), 1.0, 0.5), on(GridCell(1, 0), 0.0, 0.5))
            ellipse(at(300.0, 200.0), 200.0, 120.0) { color = "#3182ce"; dashed = true }
            note(on(GridCell(1, 0), 0.6, 0.1), 220.0, 110.0, "Anchored to the second cell")
        }
        setInitialFitAll()
    }
}
```

- Workspaces: `workspace(layout)`, `gridWorkspace`, `stripWorkspace`, `freeWorkspace<K>`, `xMediaBar<C, I>`,
  `cascadeWorkspace`, each a `HasComponents` builder returning the component.
- Content: `cell(col, row) { … }`, `item(length) { … }`, `place(key, x, y, w, h) { … }`. The block must add
  exactly one component, which becomes the item.
- Renderers as blocks: `categoryRenderer { c -> div { … } }`, `itemRenderer { c, i -> … }` on `XMediaBar`;
  `column<T> { t -> … }` and `renderer(column) { … }` on `CascadeWorkspace`.
- Doodles: `doodleLayer(placement) { … }` on any `Workspace`; `arrow`, `ellipse`, `highlight`, `stroke`,
  `marker`, `textHighlight`, `text`, `note` add to the layer and return the doodle; each takes an optional
  style block (`color`, `strokeWidth`, `opacity`, `dashed`, `render`). `at(x, y)` and `on(key, fx, fy)` build
  anchors; `doodleStyle { }` and `doodle.style { }` build styles.

## Writing your own leaf

A leaf is a `Workspace<K>` subclass that picks a layout and adds behaviour. The layout only needs `bounds`:

```java
public class RingWorkspace extends Workspace<Integer> {
    private int current = 0;
    public RingWorkspace(int count, double radius, double size) {
        super(i -> new Rect(radius * Math.cos(2 * Math.PI * i / count) - size / 2,
                            radius * Math.sin(2 * Math.PI * i / count) - size / 2, size, size));
    }
    public void next() { current = (current + 1) % itemCount(); fitTo(current); }
    public void previous() { current = (current + itemCount() - 1) % itemCount(); fitTo(current); }
    public Registration bindKeys(Component scope) {
        return KeyBindings.bind(scope, List.of("ArrowLeft", "ArrowRight"),
                key -> { if (key.equals("ArrowLeft")) previous(); else next(); });
    }
}
```

If positions depend on state (as in `XmbLayout`), give the layout a reference to that state and call
`relayout()` after changing it. Set `setAnimateItems(true)` to have the client animate the moves.

Viewport primitives available to a leaf: `fitTo(K)`, `fitTo(Collection<K>, padding)`, `fitRect(Rect, padding)`, `zoomTo(K)`, `zoomTo(Collection<K>)`, `fitAll()`, `setView(cx, cy, scale)`,
`anchorPoint(px, py, fx, fy, scale)`, `centerOn(cx, cy)`, `panBy(dx, dy)`, `setInitialFit(K)`,
`setInitialFitAll()`, `addViewChangedListener` (fires when the viewport settles, with centre and scale).

## Running the demo

![Home: one tile per view](docs/home.png)

```sh
mvn install
mvn -pl workspace-demo spring-boot:run
```

Routes: `/` home (a grid of tiles, one per view), `/grid` grid, `/xmb` cross media bar, `/cascade` cascading columns,
`/strip` strip, `/free` free canvas, `/doodles` annotation layer. Every view has a floating Home button in its top-left corner.
The screenshots in `docs/` were taken from these routes at 1280×800.

## Maven Central

The release workflow defaults to `1.1.0`, matching the current `1.1.0-SNAPSHOT` development line.
After publication, these artifacts are available from Maven Central without credentials or a custom repository:

| Group ID | Artifact ID |
| --- | --- |
| `io.github.adumeige.vaadin-workspaces` | `workspace-component` |
| `io.github.adumeige.vaadin-workspaces` | `workspace-doodles` |
| `io.github.adumeige.vaadin-workspaces` | `workspace-component-karibu` |
| `io.github.adumeige.vaadin-workspaces` | `workspace-doodles-karibu` |

```xml
<dependency>
    <groupId>io.github.adumeige.vaadin-workspaces</groupId>
    <artifactId>workspace-component</artifactId>
    <version>1.1.0</version>
</dependency>
```

The parent POM is `io.github.adumeige.vaadin-workspaces:vaadin-workspaces` at the same version.
The demo is built in CI but is excluded from publication.

Packages now start with `io.github.adumeige.vaadin.workspaces` (including `.doodles`,
`.karibu`, and `.doodles.karibu`). Update imports previously starting with `org.antoined.workspace`.
Demo packages use `io.github.adumeige.vaadin.workspaces.demo`.

## Publishing a release

Configure these repository Actions secrets, as for the other migrated repositories:

- `CENTRAL_USERNAME` and `CENTRAL_PASSWORD`: Sonatype Central user-token credentials.
- `GPG_PRIVATE_KEY`: ASCII-armored private signing key.
- `GPG_PASSPHRASE`: its passphrase.

The `io.github.adumeige` namespace must be verified in Central and the signing public key
must be available to Central. GitHub Packages, tags and Releases use the built-in
`GITHUB_TOKEN`; no extra GitHub personal access token is required.

1. Merge the migration PR and let CI pass on `main`.
2. Open Actions → **Build and publish Vaadin Workspaces** → **Run workflow**.
3. Select `main` and a new version, initially `1.1.0`.
4. The workflow verifies the libraries, tests and production demo, creates a release commit
   with versioned POMs, builds/signs once and waits for Central publication.
5. It creates an annotated `v<version>` tag and draft GitHub Release, mirrors the exact
   signed artifacts to GitHub Packages, attaches the POMs, JARs, sources and API documentation,
   then makes the GitHub Release public. The tag points at the versioned release commit;
   `main` retains its development snapshot version.

Pushes and pull requests run verification only. Java 17 remains the minimum target.
Java libraries include Javadoc; Kotlin DSL libraries include Dokka API documentation.

### Recovering an interrupted release

The `release-<version>` Actions artifact retains the signed Central bundle, release manifest
and exact source commit for 90 days, including when Central publication fails or times out.
If Central already published and only the GitHub job failed, rerun that GitHub job. It restores
those artifacts, verifies any existing package files byte for byte, skips identical uploads,
and resumes missing files. A conflicting existing artifact stops publication.

Do not rerun the Central job or start a new release with the same version after Central has
accepted it. If the Central job times out, inspect its deployment in Sonatype before retrying;
publication may have completed there. Preserve/download the retained bundle before it expires.

## License

Apache License 2.0; see [LICENSE](LICENSE).
