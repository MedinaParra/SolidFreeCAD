# SolidFreeCAD - SpaceClaim Workflow Convergence Contract

Contract version: 1.0
Status: normative for future SolidFreeCAD iterations
Source bundle studied: `workshop_instructions_trainee.zip`
Scope: interaction behavior, workflow order, UI placement, direct-edit semantics and regression gates

## 1. Purpose

This document is a permanent behavioral trace. Its purpose is to prevent new iterations from solving the latest request by removing, moving or weakening an earlier interaction.

The target is not a pixel-perfect copy of ANSYS SpaceClaim 17.0. The target is the same **working logic**:

1. select geometry or a reference;
2. choose a context-sensitive tool;
3. manipulate directly in the graphics area;
4. see an immediate reversible preview;
5. optionally type an exact value;
6. confirm once;
7. update the BRep, tree and parameters together.

SolidFreeCAD remains a hybrid product: direct modeling is the primary interaction, while the parametric tree preserves editability, recovery and FreeCAD compatibility.

---

## 2. Source trace index

The following training material was studied. Page references are PDF slide numbers.

| Trace | Source | Pages | Behavior extracted |
|---|---|---:|---|
| SRC-M1-UI | Module 01 - Core Skills | 4-12 | Interface zones, structure, options, properties, tool guides, mini toolbar and status feedback |
| SRC-M1-SEL | Module 01 - Core Skills | 13-18 | Single, loop, solid, box, additive and similarity-based selection |
| SRC-M1-ASM | Module 01 - Core Skills | 21-30 | Components, activation, shared topology and contact display |
| SRC-M1-VIEW | Module 01 - Core Skills | 31-35 | Layers, saved views, visibility, transparency and clipping |
| SRC-M1-TOOLS | Module 01 - Core Skills | 36-40 | Pull, Move, Fill and Combine as the four primary tools |
| SRC-M2-MODES | Module 02 - Creating Geometry | 4-12 | Sketch, Section and 3D modes; sketch grid and conversion to 3D |
| SRC-M2-PULL | Module 02 - Creating Geometry | 13-18 | Pull direction, Up To, revolve, sweep, scale, draft, both sides and edge behavior |
| SRC-M2-MOVE | Module 02 - Creating Geometry | 19-27 | Move handle, anchor, direction, trajectory, radial move, fulcrum, Up To and patterns |
| SRC-M2-EDIT | Module 02 - Creating Geometry | 28-36 | Fill, Blend, Combine, Split Body, Split Face and previews |
| SRC-M2-SECTION | Module 02 - Creating Geometry | 37-39 | Cross-section editing of internal or hidden geometry |
| SRC-M3-REPAIR | Module 03 - Repairing Geometry | 7-18 | Detect, preview, stitch, gaps, missing faces, merge, simplify and specialized repair |
| SRC-M3-STL | Module 03 - Repairing Geometry | 19-27, 31-40 | Facet-to-geometry workflow and mesh repair |
| SRC-M4-FEA | Module 04 - FEA Modeling | 4-26 | Welds, imprint, midsurfaces, beam extraction, extension and connectivity |
| SRC-M5-CFD | Module 05 - CFD Modeling | 4-18 | Volume extraction, enclosure, midsurface and extension |
| SRC-M6-WB | Module 06 - SpaceClaim to Workbench | 4-10 | Material, named selections, driving dimensions, parameters and transfer |
| SRC-WS1 | Workshops 1.1 and 1.2 | all | Navigation and simple bracket end-to-end creation |
| SRC-WS2 | Workshop 2.1 | all | Modes, sketch/pull, rounds, move, split, combine, fill and components |
| SRC-WS3 | Workshop 3.1 | all | Repair sequence and validation loop |
| SRC-WS4 | Workshop 4.1 | all | FEA preparation workflow |
| SRC-WS5 | Workshop 5.1 | all | CFD preparation workflow |
| SRC-WS6 | Workshop 6.1 | all | Named selections and parameter workflow |

The PDFs are reference material only and are not to be copied into the application.

---

## 3. Non-negotiable interface contract

These locations must not change without explicit user approval.

### UI-001 - Central graphics area

The 3D/2D graphics area is always the dominant region. Direct manipulation, sketching, section editing, previews, dimensions and selection feedback occur here.

### UI-002 - Left tree

The left side contains the structure and parametric tree. It may be hidden with a smooth animation and restored with an edge handle.

It contains:

- document and bodies;
- components;
- origin and reference planes;
- sketches;
- features and their dependencies;
- visibility and suppression state.

It is not the main operation launcher.

### UI-003 - Right contextual properties

The right side contains only properties and context for the current selection or active tool:

- dimensions and parameters;
- visibility and suppression;
- active plane and reference information;
- accept/cancel for detailed edits;
- tool options that cannot fit near the manipulator.

The global operation catalogue must not migrate into this panel.

### UI-004 - Bottom floating tray

Creation tools remain in an animated floating tray at the bottom. The tray is hideable and restores from a centered lower tab.

Permanent categories:

- Croquis;
- Operaciones;
- Cortes;
- Acabados;
- Patrones;
- Cuerpos;
- later: Reparar, Preparar FEA and Preparar CFD.

### UI-005 - Top command strip

The top strip contains document, history, view and panel controls. It must not become the main geometry-operation catalogue.

### UI-006 - Local tool guide

When a tool needs a secondary reference, a small contextual guide appears close to the graphics area or manipulator. Examples: Pull Direction, Up To, Revolve Axis, Sweep Path, Move Anchor, Cutter and Regions to Remove.

### UI-007 - Status and measurement feedback

The bottom/status area reports:

- active mode and tool;
- current selection;
- preview dimension;
- warnings and recoverable errors;
- recompute status;
- quick measurement.

A Python traceback must never be the primary user-facing error. Technical details may be expandable.

---

## 4. Interaction DNA

### INT-001 - Selection and context drive the command

The same tool changes behavior according to the selected entity. The application should not require a separate top-level command for every minor variation.

Examples:

- Pull a closed sketch/planar region -> solid extrusion;
- Pull an open curve -> surface;
- Pull a planar face -> offset/extrude/add/cut;
- Pull a cylindrical face -> diameter/radius edit;
- Pull an edge -> round, chamfer or edge extrusion according to option;
- Pull around an axis -> revolve;
- Pull along a path -> sweep.

### INT-002 - Both invocation orders are valid

The user may:

- select first, then choose a tool; or
- choose a tool, then select compatible geometry.

The current selection is retained when changing compatible tools.

### INT-003 - Hover preselection

Before selection, compatible geometry is highlighted. Incompatible geometry is either not highlighted or is shown with a clear rejection cue.

### INT-004 - Direct manipulation before dialog entry

A manipulator or drag gesture should be available before a modal parameter form. Exact numerical entry supplements the drag; it does not replace it.

### INT-005 - Continuous preview

During a drag:

- geometry follows the finger every display frame;
- the selected region and manipulator remain attached;
- the dimension updates without blocking the renderer;
- no definitive BRep recompute occurs for every pixel;
- release confirms and recomputes once;
- cancel restores the last committed state.

### INT-006 - Exact value entry

While dragging or immediately after selection, the user may type a dimension. The typed value updates the preview and is committed with the same accept action.

### INT-007 - Context is preserved

Closing a panel, rotating the view, zooming, or opening properties must not silently cancel the active selection or tool unless the action is incompatible.

### INT-008 - Explicit completion

Multi-step tools expose a clear state:

`select target -> select guide/reference -> preview -> complete/cancel`.

The user must always know which input the tool expects next.

---

## 5. Selection contract

### SEL-001 - Entity levels

Selectable types include point/vertex, edge/curve, face/region, surface body, solid body, plane, axis, sketch, component and feature.

### SEL-002 - Common gestures

- tap: single entity;
- double tap: connected loop/chain where meaningful;
- repeated double tap: cycle alternative loops;
- triple tap or long-press action: containing solid/body;
- Ctrl-equivalent modifier or multi-select mode: toggle items;
- Shift-equivalent mode: add items;
- clear selection: tap empty space or cancel.

### SEL-003 - Directional box selection

- left-to-right box: select only fully enclosed entities;
- right-to-left box: select all touched entities.

### SEL-004 - Power selection

After selecting a seed entity, the user can expand selection by relation:

- same radius/diameter;
- same size;
- tangent or connected chain;
- coplanar;
- parallel/perpendicular;
- same body/component;
- same feature type;
- all holes/rounds of a detected class.

### SEL-005 - Selection filters

Selection filters must be available for vertices, edges, faces, bodies, planes, sketches and components.

---

## 6. Mode state machine

```yaml
modes:
  SELECT_3D:
    purpose: select and inspect geometry
    can_enter: [SKETCH, SECTION, DIRECT_EDIT]
  SKETCH:
    requires: plane_or_planar_face
    viewport: plan_view_with_grid
    outputs:
      closed_profile: planar_region
      open_profile: curve_set
    exits:
      accept: SELECT_3D
      pull: DIRECT_EDIT
      cancel: previous_mode
  SECTION:
    requires: plane_axis_or_planar_face
    viewport: clipped_cross_section
    semantics: visible_section_edges_reference_3d_faces
    exits:
      accept: SELECT_3D
      cancel: previous_mode
  DIRECT_EDIT:
    requires: selection_and_tool
    phases: [target, optional_reference, preview, commit]
    exits:
      commit: SELECT_3D
      cancel: SELECT_3D
```

### MODE-001 - Sketch mode

Entering Sketch requires an explicit plane, planar face or valid coplanar reference set. The camera aligns normal to the plane and displays a Cartesian grid.

### MODE-002 - 3D conversion

On leaving Sketch:

- closed profiles become selectable planar regions;
- open profiles remain curves;
- Pull may immediately consume the selected profile;
- the application returns to 3D automatically after starting Pull.

### MODE-003 - Section mode

Section mode clips the model on a selected plane/axis/face. It allows direct editing of internal geometry while preserving the mapping back to 3D faces.

### MODE-004 - Mode indicator

The active mode is always visible. Mode changes animate but do not rearrange the permanent UI zones.

---

## 7. Reference-plane workflow

### PLN-001 - Default planes

Every parametric body exposes immutable XY, XZ and YZ planes.

### PLN-002 - Plane from planar face

Selecting a planar face and invoking Create Plane creates a coincident plane with the same normal. An optional signed offset creates a parallel plane.

### PLN-003 - Offset from any plane

Any existing default or derived plane can be the parent of another parallel plane. Derived planes may be chained.

### PLN-004 - Plane from references

The long-term reference resolver supports valid coplanar combinations such as:

- two coplanar lines/axes;
- a line and a point;
- three non-collinear points;
- coordinate axes and origin references.

### PLN-005 - Visible 3D representation

Visible planes are rendered as translucent selectable rectangles with local axes and labels. Visibility in the tree and viewport must be synchronized.

### PLN-006 - Plan View

A Plan View action aligns the camera with the active plane and preserves the previous view for return.

### PLN-007 - Stable dependency

A sketch and every plane-derived feature store stable references to their plane. Deleting a plane with dependents is blocked or requires explicit reassignment.

---

## 8. Sketch workflow

### SK-001 - Sketch creation

Flow:

`bottom tray -> Croquis -> choose/create plane -> Plan View -> choose primitive -> draw -> dimension -> accept`.

### SK-002 - Required primitives

- line and polyline;
- rectangle: 2-point and center variants;
- circle: center-radius/diameter and 3-point later;
- arc: center, 3-point and tangent later;
- polygon;
- point;
- construction geometry;
- projected/face curves later.

### SK-003 - In-canvas dimensions

Dimensions appear next to geometry and can be tapped to edit. Tab/next cycles active dimensions while creating geometry.

### SK-004 - Editing tools

Roadmap must retain: offset, project, corner, trim, split, fillet/chamfer, bend and scale.

### SK-005 - Multiple entities

A sketch is a collection of entities, not a single primitive. Operations consume one or more closed regions from the sketch.

### SK-006 - Constraints

At minimum: coincident, horizontal, vertical, parallel, perpendicular, tangent, concentric, equal, fixed and dimensional constraints.

### SK-007 - Construction geometry

Construction entities participate in constraints and references but do not create material.

---

## 9. Primary direct-modeling tools

## Pull

### PULL-001 - Core behavior

Pull converts or deforms selected geometry. A single Pull session may produce add, cut, offset, surface, round, chamfer, draft, revolve or sweep behavior based on context.

### PULL-002 - Direction and manipulator

Default direction is derived from geometry. The user can select an edge, axis, plane or face to redefine Pull Direction.

### PULL-003 - Up To

The user can select destination geometry from the viewport or tree. Preview terminates exactly at the destination.

### PULL-004 - Revolve

After selecting a profile/face, Revolve requests an edge/axis. Full Pull produces 360 degrees; partial angle remains editable.

### PULL-005 - Sweep

After selecting a profile, Sweep requests a path. Full Pull reaches the path end; partial progress is previewable.

### PULL-006 - Both sides and symmetric

Pull can operate one-sided, both-sided or symmetric around the source plane.

### PULL-007 - Add/cut/no-merge

The preview clearly identifies whether material will be added, removed or created as a separate body. Auto-merge must be reversible before commit.

### PULL-008 - Edge context

Pulling an edge offers round, chamfer, edge extrusion and pivot/draft options in a local guide.

## Move

### MOVE-001 - Handle

Move uses a 3D handle with three linear axes and three rotational arcs. The handle follows the selection and remains readable at any zoom.

### MOVE-002 - Anchor

The handle origin can be moved to a vertex, axis, center, face or arbitrary picked point without moving the selected geometry.

### MOVE-003 - Direction and trajectory

The user can align an axis to a selected direction or move along a selected curve/edge trajectory.

### MOVE-004 - Up To and orient

Move can terminate at destination geometry and can orient the selection to a target direction or object.

### MOVE-005 - Patterns

Copy plus linear/rotational movement creates rectangular or circular patterns while preserving count and spacing/angle parameters.

## Fill

### FILL-001 - Healing behavior

Fill removes selected holes, rounds, chamfers, protrusions, depressions or missing regions using surrounding geometry.

### FILL-002 - Preview and guide curves

The replacement is previewed. Optional guide curves constrain the filled surface.

## Combine

### COMB-001 - Target/cutter sequence

Flow:

`select target -> select cutter(s) -> preview regions -> choose regions to remove/keep -> complete`.

### COMB-002 - Boolean modes

Add, subtract and intersection are explicit but share the same selection flow.

### COMB-003 - Cutter retention

The user can retain or delete cutters before commit.

---

## 10. Structure, bodies and components

### TREE-001 - Hybrid tree

SpaceClaim itself stores objects rather than operation history. SolidFreeCAD intentionally adds a parametric feature history, but the tree must still expose bodies, surfaces, curves, planes and components as first-class objects.

### TREE-002 - Active component

Only the active component is modified by default. Operations across components require explicit multi-component selection.

### TREE-003 - Auto merge boundary

Geometry touching within the same active component may auto-merge. Bodies in different components remain separate unless Combine or shared-topology behavior is explicitly requested.

### TREE-004 - Drag/drop and visibility

Bodies and components can be reorganized, activated, hidden, isolated and opened without losing references.

---

## 11. Repair and analysis-preparation workflow traces

These are later implementation phases, but their interaction pattern is normative now.

### REP-001 - Detection cycle

`choose repair class -> scan -> highlight candidates -> inspect one-by-one or all -> preview -> complete -> rescan`.

Repair classes include stitch, gaps, missing faces, split/inexact edges, extra edges, merge/small faces and simplify.

### REP-002 - Non-destructive groups

Removed rounds or reusable selections may be stored as groups so they can be reapplied or transferred.

### REP-003 - Validation loop

After repair, the app reruns relevant checks. “No issues found” is a visible state, not an assumption.

### FEA-001 - Preparation sequence

Typical sequence:

`remove small details -> extract beams -> extract midsurfaces -> inspect contacts -> extend/move endpoints -> verify connectivity`.

### CFD-001 - Preparation sequence

Typical sequence:

`repair/solidify -> align components -> resolve interference -> share topology -> create enclosure or extract volume -> verify leaks -> name boundaries`.

### PAR-001 - Named selections

Faces, edges and bodies can be grouped, named and retained across recompute for solver transfer and repeated selection.

### PAR-002 - Driving dimensions

Pull/Move dimensions can be promoted to named driving parameters. Updating a parameter regenerates the associated direct-edit operation and preserves groups.

---

## 12. End-to-end acceptance scenarios

Each future major iteration must keep at least the applicable scenarios passing.

### FLOW-BRACKET-001 - Simple bracket

1. Create a new document.
2. Enter Sketch on XY.
3. Draw two connected rectangles and dimension them.
4. Accept and Pull the closed region into a base solid with live preview.
5. Select the top face and create circles plus a rectangular region.
6. Pull circles to add bosses and/or cut holes according to selected regions.
7. Create a vertical sketch on a side/created plane.
8. Pull the profile to add the central support.
9. Select multiple edges and Pull as rounds.
10. Select another edge set and change the local Pull option to chamfer.
11. Verify tree, dimensions, Undo/Redo and BRep all agree.

Pass condition: no command requires moving the operation catalogue away from the bottom tray, and all geometry changes preview directly in the viewport.

### FLOW-DIRECT-002 - Contextual Pull

1. Select planar face -> Pull adds/offsets.
2. Select top face -> Pull changes height.
3. Select cylindrical face -> Pull changes diameter.
4. Select edge -> Pull offers round/chamfer.
5. Select profile plus axis -> Revolve.
6. Select profile plus path -> Sweep.
7. Use Up To to terminate on another face.

Pass condition: one primary Pull tool with context guides, not seven unrelated modal workflows.

### FLOW-SECTION-003 - Internal edit

1. Select plane/axis and enter Section.
2. Pan the section location.
3. Select a cross-section edge representing an internal cylindrical face.
4. Pull Up To an adjacent internal edge.
5. Exit Section and verify the 3D body is updated.

### FLOW-REPAIR-004 - Repair loop

1. Import surface geometry.
2. Run Stitch and preview candidate edges.
3. Complete, then run Missing Faces.
4. Merge/split unwanted faces.
5. Run Gaps and Simplify.
6. Rescan and show a clean result.

### FLOW-FEA-005 - FEA preparation

1. Power-select equal-radius holes and Fill them.
2. Extract beams from cylindrical members.
3. Extract midsurfaces from thin bodies using a thickness range.
4. Show contact and identify gaps.
5. Extend or Move endpoints/edges Up To their neighbors.
6. Verify connectivity.

### FLOW-CFD-006 - CFD preparation

1. Import and repair components.
2. Orient/align attached geometry.
3. Detect and resolve interference.
4. Enable shared topology and show contacts.
5. Create an enclosure or extract internal volume.
6. Preview inlet/outlet/internal faces and detect leaks.
7. Create named selections for boundaries.

### FLOW-PARAM-007 - Parameters

1. Create named selections.
2. Direct-edit a face with Pull.
3. Promote the dimension to a named parameter.
4. Change the value from the parameter panel.
5. Recompute and preserve named selections and downstream references.

---

## 13. Current implementation status at contract creation

Status legend: `PASS`, `PARTIAL`, `MISSING`, `BETA`.

| Trace group | Status | Notes |
|---|---|---|
| UI-001..005 | PASS | Current v2.3.1 layout matches the permanent zones |
| UI-006 | PARTIAL | Yellow manipulator exists; multi-step local guides remain limited |
| UI-007 | PARTIAL | Status exists; technical tracebacks still need friendly summarization |
| INT-001 | PARTIAL | Cylinder height/diameter direct edit works; general topology context is not complete |
| INT-005 | PASS for base cylinder | GPU/VSYNC preview; needs generalization |
| SEL-001..005 | MISSING/PARTIAL | Analytical cylinder picking only; no generic BRep ray selection or power selection |
| MODE-001 | PARTIAL | Sketch overlay exists but supports one principal primitive |
| MODE-003 | MISSING | No true section mode |
| PLN-001..003 | PARTIAL | Semantic planes exist; generic face selection and viewport plane rendering are incomplete |
| PLN-005 | MISSING | Planes are not yet rendered as selectable 3D objects |
| SK-005..006 | MISSING | Multi-entity sketches and constraint solver not implemented |
| PULL-001..008 | PARTIAL/BETA | Many operations generate BRep but do not share a unified contextual Pull flow |
| MOVE-001..005 | MISSING/BETA | Parametric operations exist; direct move handle workflow incomplete |
| FILL/COMB | BETA | BRep constructions exist; selection-driven workflow incomplete |
| TREE-001 | PASS/PARTIAL | Hybrid feature tree exists; body/component semantics need expansion |
| REP/FEA/CFD/PAR | ROADMAP | Keep the defined interaction pattern when implemented |
| STEP/FCStd/FCMacro | PASS with limits | Import and macro execution available; arbitrary editable history remains limited |

---

## 14. Iteration protocol

Every future development iteration must follow this protocol.

### Before coding

1. Read this contract.
2. List the trace IDs affected.
3. State which existing traces must remain unchanged.
4. Add or update an acceptance scenario before replacing UI behavior.
5. Check whether the change belongs to the bottom tray, right properties panel, local tool guide or viewport.

### During coding

1. Do not remove old behavior to simplify new behavior.
2. Keep direct preview independent from BRep recompute.
3. Keep tree, model, selection and properties synchronized.
4. Preserve file opening for STEP, FCStd and FCMacro.
5. Preserve Undo/Redo and error recovery.

### Before delivering

Report a regression matrix with:

- layout invariants UI-001..007;
- bracket flow FLOW-BRACKET-001;
- direct Pull flow FLOW-DIRECT-002;
- plane/sketch behavior;
- animated drag behavior;
- STEP/FCStd/FCMacro opening;
- ARM32/ARM64 packaging;
- device test status separately from CI status.

A CI build passing compilation is not evidence that the interaction workflow passes on a device.

---

## 15. Definition of done for workflow parity

A behavior is complete only when all are true:

1. it is reachable through the permanent interface contract;
2. it has hover/selection feedback;
3. it has a live preview when geometric;
4. it supports cancel without corrupting the last committed BRep;
5. it supports exact values where dimensional;
6. it updates tree and properties after commit;
7. it participates in Undo/Redo;
8. it has a device-verifiable acceptance scenario;
9. it does not regress an earlier trace ID;
10. its current limitations are explicitly declared.

---

## 16. Machine-readable invariant summary

```yaml
contract: solidfreecad-spaceclaim-workflow
version: 1.0
layout:
  left: structure_and_feature_tree
  center: graphics_and_direct_manipulation
  right: contextual_properties_only
  bottom: floating_creation_and_operation_tray
  top: document_history_view_and_panel_controls
modes: [SELECT_3D, SKETCH, SECTION, DIRECT_EDIT]
primary_tools: [PULL, MOVE, FILL, COMBINE]
preview:
  cadence: display_vsync
  brep_recompute_during_drag: false
  commit_on_release_or_accept: true
  cancel_restores_committed_shape: true
required_files: [STEP, FCStd, FCMacro]
regression_flows:
  - FLOW-BRACKET-001
  - FLOW-DIRECT-002
  - FLOW-SECTION-003
  - FLOW-REPAIR-004
  - FLOW-FEA-005
  - FLOW-CFD-006
  - FLOW-PARAM-007
forbidden_regressions:
  - move_operations_from_bottom_to_right
  - remove_sketch_creation
  - replace_live_drag_with_numeric_only_dialog
  - recompute_brep_for_every_motion_event
  - expose_raw_traceback_as_primary_error
  - remove_previous_file_format_support
```

This contract must evolve by adding trace IDs or explicitly superseding them. Existing IDs are never silently redefined.
