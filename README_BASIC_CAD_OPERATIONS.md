# SolidFreeCAD basic CAD operations

This milestone uses the official SOLIDWORKS feature terminology and interaction model as a functional reference while keeping an independent FreeCAD/OpenCASCADE implementation.

Official reference pages:

- SOLIDWORKS Features toolbar (2025/2026)
- Features overview
- Sketch workflow with extruded and revolved bosses/bases
- FeatureManager design tree and PropertyManager behavior

## Implemented operation catalog

### Sketch-based material operations

- Boss extrude
- Boss revolve
- Boss sweep
- Boss loft
- Rib

### Remove-material operations

- Cut extrude
- Cut revolve
- Cut sweep
- Cut loft
- Simple hole
- Counterbore hole
- Countersink hole

### Dress-up operations

- Uniform rounded prism (`BETA`)
- Chamfered prism (`BETA`)
- Cylindrical shell (`BETA`)
- Drafted/conical solid (`BETA`)

### Transform and pattern operations

- Linear pattern
- Circular pattern
- Mirror
- Move/copy body

### Body operations

- Combine add/fuse
- Combine subtract/cut
- Combine common/intersection

## Parametric architecture

Every operation is stored as a semantic feature node with a stable identifier, label, suppression state and editable numeric parameter map. The feature program is regenerated as a FreeCAD-compatible macro and evaluated by the embedded CPython + FreeCAD Base + OpenCASCADE runtime.

The first cylinder retains GPU/VSYNC direct-face editing. When several features exist, generic face inference is disabled to avoid changing the wrong feature until stable topological naming and arbitrary-face mapping are available.

## FCMacro support

The launcher registers `ACTION_VIEW` for `.FCMacro` files and common Python macro MIME types. The workbench also detects macros by extension, MIME type and source signature. UTF-8 BOM and Windows line endings are normalized, NUL bytes are rejected, and macro input is limited to 4 MB.

Supported macro entry points include:

- `import FreeCAD as App`
- `import Part`
- document creation and recompute
- box, cylinder, sphere, cone and torus
- polygon face extrusion
- fuse, cut and common
- placements and translations
- `Part.show()`

## Current boundaries

The feature catalog is broad, but this is not full desktop SOLIDWORKS or desktop FreeCAD parity. General edge-selected fillets, arbitrary-face shelling, guide-curve sweeps, multi-section freeform lofts, Hole Wizard standards databases, stable topological naming and full Sketcher constraint solving remain separate milestones. The UI marks construction-based dress-up commands as `BETA` rather than presenting them as general operations.
