# SolidFreeCAD 3.1.1 Samsung A26 safe-start gate

This synchronization commit triggers the safe-start Android build after the workflow is present in the branch history.

The build must defer CPython and FreeCAD-Native initialization until a model operation is requested, preserve ARM32 and ARM64 packaging, verify 16 KB ELF alignment and keep main unchanged.
