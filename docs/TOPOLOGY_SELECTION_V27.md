# SolidFreeCAD 2.7 - topology selection and loops

This iteration advances the convergence contract without moving any permanent interface zone.

## Preserved traces

- UI-001..007: left tree, central viewport, right contextual properties, bottom floating tray and top document strip.
- INT-005: GPU/VSYNC direct preview for the established parametric cylinder.
- SK-001..007 as implemented in v2.6.
- PLN-001..007 as implemented in v2.5.
- IND-REC/ERR/LOG/SAFE from v2.4.
- STEP, FCStd, FCMacro, Undo/Redo and ARM32/ARM64 packaging.

## Added traces

- SEL-001A: selectable faces, feature edges, feature vertices and face-boundary loops.
- SEL-002A: persistent viewport selection filter: Auto, Face, Edge, Vertex and Loop.
- SEL-003A: Auto prioritizes a nearby feature vertex, then feature edge, then face.
- SEL-004A: sharp/boundary edges are derived from surface-cluster adjacency, not raw triangle diagonals.
- SEL-005A: a selected planar face reports all boundary loops, including future inner regions.
- MEAS-001: approximate face area, edge length, vertex coordinates and loop perimeter are available in contextual properties.
- VIS-001: faces use orange fill, edges yellow lines, vertices orange points and loops magenta lines.

## Topology strategy

1. Triangles are clustered into continuous rendered surfaces.
2. Mesh edges internal to the same surface are ignored as tessellation artifacts.
3. Edges separating surface clusters or open boundaries become feature edges.
4. Face boundary edges are ordered into closed loops or open chains.
5. Ray picking is occlusion-aware and uses a screen-derived world tolerance.
6. Topology is rebuilt only after a definitive BRep commit, never during live drag.

## Acceptance gates

- Cube top face selects two triangles, reports area 1 and one four-edge boundary loop.
- The triangle diagonal of the top face is not exposed as a selectable feature edge.
- Edge selection reports the geometric edge length.
- Vertex selection reports coordinates and incident feature-edge count.
- Loop selection returns an ordered closed perimeter with the first point repeated.
- Changing the filter clears incompatible selection without changing the model.
- The established face-driven extrusion animation remains available in Auto/Face selection.

## Explicit limits

- Stable BRep names still come from the committed triangulation and are not yet OCCT topological names.
- Loop detection is boundary-based; tangent-chain expansion and Power Selection are later steps.
- This iteration selects and measures topology. General Pull/Move commit on arbitrary imported faces remains the next gate.
- Device validation is still required for pick tolerance, line width and glove-mode usability.
