# Integrating the overlay into official FreeCAD 1.1.1

## 1. Prepare official source

Run:

```bash
./desktop/scripts/prepare-freecad-1.1.1.sh /path/to/SolidFreeCAD-Desktop
```

Copy:

```text
desktop/overlay/src/Gui/SolidFreeCAD
```

into:

```text
src/Gui/SolidFreeCAD
```

## 2. Add the CMake directory

In `src/Gui/CMakeLists.txt`, after the `FreeCADGui` target exists, add:

```cmake
add_subdirectory(SolidFreeCAD)
```

The overlay uses `target_sources(FreeCADGui ...)`, so it becomes part of the official GUI library rather than a parallel application.

## 3. Install the first shell

In `src/Gui/MainWindow.cpp`, add:

```cpp
#include "SolidFreeCAD/SolidGuiBootstrap.h"
```

Near the end of `Gui::MainWindow` construction, after standard commands and the main window UI are initialized, add:

```cpp
SolidFreeCAD::installGui(this);
```

At the beginning of the main-window destructor, add:

```cpp
SolidFreeCAD::uninstallGui();
```

The exact insertion locations must be selected after reviewing the 1.1.1 constructor and shutdown order. The first patch must not run before `Gui::Application::Instance` and standard commands exist.

## 4. First validation

Verify that the new toolbar appears and that these registered commands execute:

- `Std_New`
- `Std_Open`
- `Std_Save`
- `Std_Undo`
- `Std_Redo`
- `Std_ViewFitAll`
- `Std_ViewAxonometric`

## 5. Safety constraints

- Do not remove the classic menu or toolbars in the first build.
- Do not replace `Gui::View3DInventor`.
- Do not write directly into FCStd archives.
- Do not duplicate PartDesign or Sketcher algorithms.
- Keep every SolidFreeCAD source file inside the isolated namespace and directory.
