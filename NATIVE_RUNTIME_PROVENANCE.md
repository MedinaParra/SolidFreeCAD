# SolidFreeCAD Android native runtime provenance

This branch does not treat a filename or UI state as proof that a CAD backend is active.
The Android build downloads two immutable GitHub Actions artifacts from
`MedinaParra/FreeCAD-Native`, verifies their published SHA-256 files and records the
resolved artifact hashes inside the APK.

## Runtime inputs

| Purpose | Repository | Commit | Workflow run | Artifact |
|---|---|---|---:|---|
| FreeCAD Base, OCCT, CPython, STEP and macro runtime | `MedinaParra/FreeCAD-Native` | `1f70422ebaf60861971086709352d50716304625` | `29699041993` | `freecad-android-base-runtime-v0110-apk` |
| Object-aware FCStd BRep bridge | `MedinaParra/FreeCAD-Native` | `1f70422ebaf60861971086709352d50716304625` | `29699041982` | `freecad-android-fcmacro-fcstd-apk` |

## Packaged native entry points

- `libfreecad_android_core.so`: document model, OCCT primitives and boolean operations,
  recompute, tessellation, CPython macros and native STEP import.
- `libfreecad_files_core.so`: object-aware FCStd BRep import. It is produced from the
  second artifact and assigned a distinct ELF SONAME during packaging.
- `NativeBackendRegistry`: the only Kotlin authority allowed to call
  `System.loadLibrary`.

## Current guarantees

- ABI packaging: `armeabi-v7a` and `arm64-v8a`.
- SHA-256 validation of both downloaded APK inputs.
- Transitive ELF dependency closure check for every packaged `.so`.
- Required JNI symbol check for macro, STEP, FreeCAD Base and FCStd entry points.
- APK content inspection, APK SHA-256 and an embedded `runtime-provenance.txt`.

## Boundaries

- Topological selection in SolidFreeCAD 2.7/2.8 is reconstructed from the confirmed
  display mesh; identifiers are not persistent OCCT topological names.
- FCStd export preserves untouched ZIP entries and unknown XML properties while
  editing supported universal metadata. It is not proof that every Workbench object
  can be parametrically recomputed or edited on Android.
- A green CI build proves compilation, unit tests, packaged resources and ELF/JNI
  contracts. Physical-device stability, GPU context recovery and long-session memory
  behavior remain separate validation tasks.
