package com.medinaparra.freecadandroid.runtime

/** Immutable description of the validated native base consumed by SolidFreeCAD. */
object BaseRuntimeDescriptor {
    const val runtimeVersion = "0.11.0"
    const val freeCadVersion = "1.1.1"
    const val sourceCommit = "1f70422ebaf60861971086709352d50716304625"
    const val pythonVersion = "3.14"

    val packagedAbis: List<String> = listOf("arm64-v8a", "armeabi-v7a")

    val capabilities: Set<String> = linkedSetOf(
        "FreeCAD Base::Vector3d",
        "FreeCAD Base::Matrix4D",
        "OpenCASCADE BRep",
        "STEP import",
        "FCStd object-aware import",
        "FCStd labels, visibility and placement",
        "FCStd App::Link and XLink resolution",
        "Non-destructive FCStd metadata export",
        "Embedded CPython",
        "FreeCAD-style macros",
        "Part primitives and booleans",
        "Polygon prism extrusion",
        "Atomic dependency recompute",
        "Bounded expressions and rollback",
        "SolidFreeCAD parametric bridge",
        "GPU synchronized face preview"
    )

    fun selectAbi(deviceAbis: List<String>): String? =
        deviceAbis.firstOrNull(packagedAbis::contains)

    fun pythonAssetPath(abi: String): String {
        require(abi in packagedAbis) { "ABI no empaquetada: $abi" }
        return "python-runtime/$abi.zip"
    }

    fun displayName(): String =
        "FreeCAD $freeCadVersion Base · Runtime $runtimeVersion"
}
