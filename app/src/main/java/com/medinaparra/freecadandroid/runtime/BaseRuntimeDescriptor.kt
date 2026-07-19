package com.medinaparra.freecadandroid.runtime

/** Immutable description of the validated native base consumed by SolidFreeCAD. */
object BaseRuntimeDescriptor {
    const val runtimeVersion = "0.9.1"
    const val freeCadVersion = "1.1.1"
    const val sourceCommit = "ac7cc2213aa26ed1526f522b5338b6a625fc9937"
    const val pythonVersion = "3.14"

    val packagedAbis: List<String> = listOf("arm64-v8a", "armeabi-v7a")

    val capabilities: Set<String> = linkedSetOf(
        "FreeCAD Base::Vector3d",
        "FreeCAD Base::Matrix4D",
        "OpenCASCADE BRep",
        "STEP import",
        "FCStd BRep initial import",
        "Embedded CPython",
        "FreeCAD-style macros",
        "Part primitives and booleans",
        "Polygon prism extrusion",
        "SolidFreeCAD parametric bridge"
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
