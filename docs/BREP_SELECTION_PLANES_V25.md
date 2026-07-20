# SolidFreeCAD 2.5 - BRep selection and visible reference planes

This iteration advances the SpaceClaim workflow convergence contract without changing its permanent interface zones.

## Trace impact

Preserved:

- `UI-001..007`: left tree, central viewport, right contextual properties and bottom floating tray;
- `INT-005`: GPU/VSYNC direct preview for the base cylinder;
- `FLOW-BRACKET-001` and `FLOW-DIRECT-002` where already implemented;
- industrial recovery, diagnostics, glove mode and safe BRep rollback from v2.4;
- STEP, FCStd and FCMacro opening.

Advanced:

- `SEL-001`: tap selection now ray-tests the committed BRep triangulation instead of only an analytical cylinder;
- `SEL-002`: connected triangles are grouped across smooth shared edges while sharp edges stop propagation;
- `PLN-003`: a reference plane or a sketch plane can be created from any selected planar BRep surface;
- `PLN-005`: visible reference planes are drawn as translucent selectable objects in the 3D viewport;
- `UI-006`: the contextual properties panel describes the selected surface and exposes plane creation when valid.

## Implementation

1. A topology cache is rebuilt only after a definitive BRep mesh is committed.
2. A tap creates a world-space ray and performs Möller-Trumbore triangle intersection.
3. The nearest triangle is expanded through shared edges using a normal-continuity threshold.
4. Planarity is verified from normal agreement and distance to a common plane.
5. The selected surface is rendered as an orange overlay.
6. Reference planes are rendered after the solid with translucent fill and a selectable outline.
7. The proven cylinder-specific yellow manipulator remains available and keeps its GPU/VSYNC preview path.

## Acceptance checks

1. Tap the top face of a box or cylinder: the connected planar surface is highlighted orange.
2. Tap a side face separated by a sharp edge: only that face cluster is selected.
3. Select a planar surface and choose **Plano paralelo a esta cara**: the new plane stores the selected point and normal.
4. Select a planar surface and create a sketch from the bottom tray: a coincident face-reference plane and sketch are created.
5. Show XY, XZ, YZ and derived planes: translucent rectangles appear in the viewport.
6. Tap a visible plane outside the solid: its tree/properties entry becomes selected.
7. Drag the yellow arrow on the base cylinder: the existing fluid GPU animation remains unchanged.
8. Force a BRep failure: the v2.4 safe rollback and diagnostic code remain active.

## Current limits

- Ray picking is linear in triangle count on each tap. It does not run during orbit, pan or animated drag.
- Surface adjacency relies on shared triangle vertex indices. Some imported triangulations with duplicated seam vertices may produce smaller face clusters.
- This is visual/topological selection over the triangulated BRep; persistent FreeCAD topological naming is not yet implemented.
- Edge, vertex, loop, body and power-selection modes remain later milestones.
- Imported STEP/FCStd faces are selectable and can define planes, but are not yet mapped to arbitrary editable feature parameters.
- General Pull and Move manipulators over arbitrary selected faces are the next milestone.

## Industrial status

CI validates compilation, topology unit tests and APK packaging. Physical-device validation is still required for selection precision, large-mesh latency, thermal behavior and glove operation before this milestone can be called workshop-proven.
