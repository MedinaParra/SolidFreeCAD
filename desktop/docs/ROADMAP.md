# SolidFreeCAD Desktop Roadmap

## Milestone 0 — Official baseline

- Create final repository from official FreeCAD tag `1.1.1`.
- Configure `origin` and `upstream` remotes.
- Build unmodified FreeCAD on Ubuntu.
- Record compiler, Qt, Python, OpenCASCADE and Coin3D versions.
- Run smoke tests opening and saving FCStd, STEP and BREP files.

Exit condition: an unmodified FreeCAD 1.1.1 package built from the final repository launches successfully.

## Milestone 1 — Solid GUI shell

- Add `src/Gui/SolidFreeCAD` to the official GUI build.
- Install `SolidGuiManager` after the main window is created.
- Add a prototype ribbon toolbar.
- Add command lookup and safe invocation.
- Add a preference for `Classic` and `SolidFreeCAD` modes.
- Preserve the official MDI area and ComboView.

Exit condition: the SolidFreeCAD shell can trigger new, open, save, undo, redo and standard view commands.

## Milestone 2 — Mechanical modeling ribbon

- Load Part Design and Sketcher commands into grouped ribbon pages.
- Add contextual enable/disable behavior.
- Support Body, Sketch, Pad, Pocket, Fillet and Chamfer.
- Add user-facing error messages for unavailable commands.
- Add command search.

Exit condition: a basic constrained part can be created and edited exclusively from the new shell.

## Milestone 3 — Feature and property managers

- Restyle the official tree and property panel.
- Add feature filtering and status indicators.
- Integrate TaskView with large accept/cancel controls.
- Preserve selection synchronization and edit modes.

Exit condition: object selection, property editing and active task panels work without opening classic toolbars.

## Milestone 4 — File workflows

- Recent files and welcome screen.
- Import STEP, IGES, BREP and STL.
- Save and reopen FCStd with regression checks.
- Recovery and autosave integration.
- File-association packaging for Ubuntu.

Exit condition: representative official FreeCAD files survive open-edit-save-reopen cycles.

## Milestone 5 — Assembly and drawings

- Assembly command groups and component tree behavior.
- TechDraw pages, views, sections and dimensions.
- PDF and DXF export.

Exit condition: an assembly and a production drawing can be completed from SolidFreeCAD mode.

## Milestone 6 — Packaging and release

- Ubuntu AppImage x86-64.
- Ubuntu ARM64 build where dependencies permit.
- Debian package.
- GitHub Actions build matrix.
- Automated command availability tests.
- Crash reporting and diagnostic bundle.

Exit condition: a versioned beta package installs and runs on supported Ubuntu systems.

## Immediate backlog

1. Verify the exact official FreeCAD 1.1.1 tag and source commit during bootstrap.
2. Build official source without GUI changes.
3. Integrate the overlay CMake target.
4. Display a first ribbon prototype.
5. Bind six standard commands.
6. Capture the first Ubuntu screenshot and build log.
