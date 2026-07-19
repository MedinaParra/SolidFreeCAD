# SolidFreeCAD 1.6 — Face-driven workspace

## Interaction model

- Tap the planar top face to highlight it in orange and show a yellow 3D axial manipulator.
- Drag the yellow manipulator to update the existing `Saliente-Extruir1` depth.
- Tap the cylindrical side face to highlight it in orange and show a yellow radial manipulator.
- Drag the radial manipulator to update the diameter stored in `Croquis1`.
- The feature tree remains `Cuerpo1 -> Saliente-Extruir1 -> (-) Croquis1`; direct manipulation does not create extra history nodes.

## Editors

- `Saliente-Extruir1` uses a SolidWorks-style property panel with source plane, termination mode, direction, numeric depth, accept and cancel.
- `Croquis1` opens a graphical sketch editor with a filled profile, center axes, blue diameter dimension and numeric field inside the viewport.
- Side panels can be hidden or restored with one action for phone and tablet use.

## Rendering

- OpenGL ES 2.0.
- Ray-based analytical selection for the current parametric cylinder.
- True orange triangle overlay for selected top or side faces.
- Yellow manipulator geometry is rendered in model coordinates and remains attached during orbit, pan and zoom.
- One finger orbits when the manipulator is not captured; two fingers pan; pinch zooms.

## Runtime

- FreeCAD Base 1.1.1 runtime 0.9.1.
- OpenCASCADE, STEP, initial FCStd BRep loading and CPython.
- `armeabi-v7a` and `arm64-v8a`.

## Current scope

Face picking and parameter mapping are implemented for the controlled circular sketch plus extrusion feature. Imported STEP and FCStd geometry remains viewable as native BRep but is not automatically mapped to editable parametric history.
