# SolidFreeCAD 3.0 CI gate

This gate validates the adaptive transactional STEP editing milestone.

The exact source commit must compile and verify:

- adaptive phone/tablet workbench policy and safe touch targets;
- repeated-tap selection ladder;
- persistent FreeCAD-Native STEP session bindings;
- OCCT face identifier mapping per triangle;
- planar Pull preview, commit, rollback, save-copy and close JNI symbols;
- inherited sketch, topology, recovery and file-opening tests;
- universal ARM32 and ARM64 APK packaging.

A green CI build does not replace the required physical save/reopen test on target Android devices.
