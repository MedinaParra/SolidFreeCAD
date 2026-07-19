# SolidFreeCAD Desktop Bootstrap

This directory starts the Ubuntu desktop edition of SolidFreeCAD.

## Product decision

SolidFreeCAD Desktop is not a separate CAD kernel. It is a new Qt/C++ interface layer built on the official FreeCAD 1.1.1 source tree.

The project keeps the official FreeCAD document model, FCStd persistence, OpenCASCADE geometry, Coin3D/OpenGL viewer, Python runtime, workbenches, commands, TaskView and property system.

## Official baseline

- Upstream repository: `FreeCAD/FreeCAD`
- Required tag: `1.1.1`
- Target platform: Ubuntu Linux x86-64 first
- GUI technology: Qt Widgets and C++
- Initial integration mode: optional `SolidFreeCAD` interface mode

## Current bootstrap contents

- `docs/ARCHITECTURE.md`: integration boundaries and design rules.
- `docs/ROADMAP.md`: staged implementation plan.
- `scripts/prepare-freecad-1.1.1.sh`: prepares a clean official source checkout.
- `overlay/src/Gui/SolidFreeCAD`: first GUI manager and command bridge.

## First milestone

The first runnable milestone must:

1. Build the unmodified official FreeCAD 1.1.1 source on Ubuntu.
2. Add a SolidFreeCAD GUI mode without replacing the CAD kernel.
3. Show a Qt ribbon prototype above the official MDI area.
4. Invoke registered FreeCAD commands by name.
5. Preserve the classic interface as a compatibility mode.

## Migration into the final repository

The final repository should be `MedinaParra/SolidFreeCAD-Desktop`, created as a fork or full source derivative of `FreeCAD/FreeCAD` at tag `1.1.1`. The contents of `desktop/overlay` are designed to be copied into that source tree.
