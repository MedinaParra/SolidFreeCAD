# SolidFreeCAD Desktop Architecture

## 1. Core principle

The official FreeCAD 1.1.1 source is the product foundation. SolidFreeCAD changes the interaction shell, not the CAD engine.

## 2. Components kept from FreeCAD

The first implementation must keep these official systems intact:

- `Base` and `App` libraries.
- `App::Document`, properties, expressions, transactions and recompute graph.
- FCStd read/write.
- Part and PartDesign geometry backed by OpenCASCADE.
- Sketcher constraints and solver.
- Assembly, TechDraw, FEM and other official modules.
- Python interpreter and macro recording.
- `Gui::CommandManager` and registered command names.
- `Gui::View3DInventor` and ViewProviders.
- `Gui::TaskView` and property editors.
- Workbench activation and module loading.

## 3. Components introduced by SolidFreeCAD

The new layer will initially provide:

- `SolidGuiManager`: installs and removes SolidFreeCAD widgets.
- `SolidCommandBridge`: invokes existing FreeCAD commands by name.
- `SolidRibbon`: grouped command presentation.
- `SolidQuickAccessBar`: new/open/save/undo/redo/view controls.
- `SolidWelcomePage`: recent documents and templates.
- `SolidFeatureManager`: styled document tree facade.
- `SolidPropertyManager`: unified property and task presentation.
- `SolidThemeManager`: stylesheet, metrics and icons.

## 4. Integration strategy

### Stage A: non-destructive shell

Keep `Gui::MainWindow`, the official MDI area, ComboView, TaskView and 3D view. Add a new top-level ribbon and style the existing panels. Classic toolbars remain available behind a preference.

### Stage B: adapted panels

Replace visual presentation around the document tree and property/task panels while retaining their official models and controllers.

### Stage C: default SolidFreeCAD mode

After command coverage and regression tests are complete, make SolidFreeCAD the default shell while keeping Classic mode available.

## 5. Command rule

No ribbon button may duplicate CAD logic. Every production button must resolve a registered FreeCAD command or call an official application API inside an undo transaction.

Examples:

- `Std_New`
- `Std_Open`
- `Std_Save`
- `Std_Undo`
- `Std_Redo`
- `Std_ViewFitAll`
- `Std_ViewIsometric`
- `PartDesign_Body`
- `PartDesign_NewSketch`
- `PartDesign_Pad`
- `PartDesign_Pocket`
- `PartDesign_Fillet`
- `PartDesign_Chamfer`

Command names must be verified against the official 1.1.1 source before being enabled in the production ribbon.

## 6. Viewer rule

The Android Canvas viewer is not reused for desktop geometry. The official `Gui::View3DInventor` remains responsible for rendering, selection, preselection, camera state, clipping and ViewProvider updates.

## 7. Compatibility rule

A SolidFreeCAD-produced `.FCStd` document must remain openable in unmodified FreeCAD 1.1.1 whenever the document uses official object types. Custom objects require explicit fallback and migration behavior.

## 8. Upstream maintenance

The repository must keep an `upstream` remote pointing to `FreeCAD/FreeCAD`. GUI changes should be isolated under `src/Gui/SolidFreeCAD` and narrow integration patches to reduce conflicts when adopting later FreeCAD releases.
