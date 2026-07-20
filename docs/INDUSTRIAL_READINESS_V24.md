# SolidFreeCAD 2.4 - Industrial hardening gate

This iteration implements reliability traces without changing the permanent UI contract.

## Trace impact

Preserved: UI-001..007, INT-001..005, FLOW-BRACKET-001, FLOW-DIRECT-002, STEP/FCStd/FCMacro.

Added:

- IND-REC-001: checksummed atomic autosave after successful BRep commit.
- IND-REC-002: previous valid snapshot retained as fallback.
- IND-REC-003: last parametric workshop session restored at startup.
- IND-ERR-001: primary UI never displays a raw Python/FreeCAD traceback.
- IND-ERR-002: each failure receives a stable CAD diagnostic code.
- IND-LOG-001: rotating offline technical log, exportable by the operator.
- IND-TOUCH-001: persistent glove mode enlarges the main command strip and bottom operation tiles.
- IND-SAFE-001: failed recompute keeps the last committed mesh, tree and history.

## Acceptance checks

1. Create or edit the base extrusion, close the app and reopen: the last committed model is recovered.
2. Corrupt the current recovery file: the previous valid snapshot is used or a new safe document is created.
3. Force a macro failure: the viewport retains the last valid model and shows a short Spanish message plus CAD code.
4. Export diagnostics from Taller: the report includes app/runtime/device metadata and the technical trace.
5. Enable glove mode, restart the app: larger touch targets remain enabled.
6. Open STEP, FCStd and FCMacro after enabling industrial features: existing formats remain functional.

## Limits

- Recovery stores SolidFreeCAD parametric programs, not editable history reconstructed from imported STEP/FCStd.
- CI validates serialization and packaging; workshop-device endurance, thermal behavior and repeated file-cycle testing still require physical testing.
- The Android process is not yet isolated from a native FreeCAD crash; process isolation is a later hardening level.
