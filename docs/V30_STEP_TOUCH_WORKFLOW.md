# SolidFreeCAD 3.0 — adaptive STEP touch editing

## Implemented product flow

1. Open a STEP file through Android's document picker.
2. Retain the imported `TopoDS_Shape` inside a FreeCAD-Native session.
3. Render one OCCT face identifier per triangle.
4. Select a face directly in the viewport.
5. Repeat a tap to cycle vertex, edge, loop and face candidates under the finger.
6. Enter a signed Pull distance in millimetres.
7. Preview the native boolean without replacing the committed BRep.
8. Confirm or cancel the preview.
9. Save the committed revision as a new STEP copy.

## Mobile and tablet rules

- Normal touch targets are at least 48 dp; glove mode raises them to 58 dp.
- Compact screens keep only one persistent side panel open.
- Expanded tablets may retain tree and properties together.
- Selection controls scroll horizontally instead of shrinking below safe finger size.
- The activity supports sensor rotation instead of forcing landscape.
- The original STEP source is never overwritten.

## Current limits

- Direct Pull accepts planar OCCT faces only.
- Face IDs are revision-local and are rebuilt after commit.
- The first UI uses an exact numeric Pull field; direct drag for arbitrary STEP faces is a later refinement.
- Physical save/reopen testing on target devices remains required before production status.
