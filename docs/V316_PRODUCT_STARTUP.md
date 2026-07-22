# SolidFreeCAD 3.1.6 product startup

This milestone converts the verified Android 16 diagnostic bootstrap into a product startup flow. It auto-opens healthy CAD sessions, falls back to safe mode after interrupted renderer loading, forwards STEP/FCStd/FCMacro intents to the isolated CAD process, and keeps technical diagnostics collapsed by default.

The branch remains isolated and must not be merged into main without explicit authorization.
