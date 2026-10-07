# vaadin-workspaces

A pannable, zoomable grid workspace component for Vaadin Flow. Cells are laid out on a fixed-size grid, and the viewport can fit or zoom to any cell, or show them all at once.

## Modules

- `workspace-component` — the `GridWorkspace` component (Java, with a TypeScript web component).
- `workspace-demo` — a Spring Boot + Kotlin demo application.

## Requirements

- Java 17
- Maven
- Vaadin 24.10, Spring Boot 3.5

## Usage

```java
GridWorkspace workspace = new GridWorkspace(800, 600); // cell width, height in px
workspace.setSize("100%", "100%");
workspace.setCellMargin(12);

workspace.addCell(0, 0, new Span("First"));
workspace.addCell(1, 0, new Span("Second"));

workspace.setInitialFitAll();
workspace.bindArrowKeys(layout); // arrow keys pan by one cell
workspace.addCellFocusedListener(e -> System.out.println(e.getCol() + "," + e.getRow()));
```

Navigation:

| Method | Effect |
| --- | --- |
| `fitAll()` | Show every cell |
| `fitToCell(col, row)` | Move to a cell |
| `zoomToCell(col, row)` | Zoom in on a cell |
| `moveLeft()` / `moveRight()` / `moveUp()` / `moveDown()` | Pan by one cell |
| `setDraggingEnabled(boolean)` / `setZoomingEnabled(boolean)` | Toggle mouse drag and zoom |

## Running the demo

```sh
mvn install
mvn -pl workspace-demo spring-boot:run
```

Then open http://localhost:8080.
