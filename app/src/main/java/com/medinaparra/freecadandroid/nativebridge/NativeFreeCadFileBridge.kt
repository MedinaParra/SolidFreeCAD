package com.medinaparra.freecadandroid.nativebridge

import com.medinaparra.freecadandroid.io.FcStdImportRegistry
import com.medinaparra.freecadandroid.io.FcStdObjectRecord
import com.medinaparra.freecadandroid.model.SceneMesh

data class NativeFreeCadFilePayload(
    val vertices: FloatArray,
    val indices: IntArray,
    val bounds: FloatArray,
    val summary: String
) {
    init {
        require(bounds.size == 6) { "FCStd bounds must contain six values" }
    }

    fun toSceneMesh(): SceneMesh = SceneMesh(
        vertices = vertices,
        indices = indices,
        minX = bounds[0],
        minY = bounds[1],
        minZ = bounds[2],
        maxX = bounds[3],
        maxY = bounds[4],
        maxZ = bounds[5]
    )
}

data class NativeFreeCadFileScene(
    val mesh: SceneMesh,
    val summary: String,
    val displayName: String
)

/**
 * FCStd-specific bridge. It is intentionally packaged as a second shared object
 * so the official FreeCAD Base 0.11 core and the object-aware FCStd importer can
 * coexist without replacing each other's JNI contracts.
 */
object NativeFreeCadFileBridge {
    val isAvailable: Boolean
        get() = NativeBackendRegistry.fcStdLoaded

    private external fun nativeImportBrepFiles(
        localPaths: Array<String>,
        linearDeflection: Double,
        angularDeflection: Double
    ): NativeFreeCadFilePayload

    private external fun nativeImportBrepObjects(
        localPaths: Array<String>,
        objectNames: Array<String>,
        placementValues: DoubleArray,
        visibilityValues: BooleanArray,
        linearDeflection: Double,
        angularDeflection: Double
    ): NativeFreeCadFilePayload

    fun importFcStdBreps(
        localPaths: List<String>,
        displayName: String,
        archiveSummary: String,
        linearDeflection: Double = 0.35,
        angularDeflection: Double = 0.30
    ): NativeFreeCadFileScene {
        check(isAvailable) { "El puente FCStd FreeCAD 0.11 no está disponible" }
        require(localPaths.isNotEmpty()) { "El documento FCStd no contiene geometría BREP compatible" }

        val records = FcStdImportRegistry.resolve(localPaths)
        return if (records.size == localPaths.size) {
            importFcStdObjects(records, displayName, archiveSummary, linearDeflection, angularDeflection)
        } else {
            val payload = nativeImportBrepFiles(
                localPaths.toTypedArray(),
                linearDeflection,
                angularDeflection
            )
            NativeFreeCadFileScene(
                mesh = payload.toSceneMesh(),
                summary = archiveSummary + "\n" + payload.summary,
                displayName = displayName
            )
        }
    }

    fun importFcStdObjects(
        objects: List<FcStdObjectRecord>,
        displayName: String,
        archiveSummary: String,
        linearDeflection: Double = 0.35,
        angularDeflection: Double = 0.30
    ): NativeFreeCadFileScene {
        check(isAvailable) { "El puente FCStd FreeCAD 0.11 no está disponible" }
        val shapeObjects = objects.filter { it.brepFile != null }
        require(shapeObjects.isNotEmpty()) { "El documento FCStd no contiene objetos Shape compatibles" }

        val placements = DoubleArray(shapeObjects.size * 7)
        shapeObjects.forEachIndexed { index, record ->
            val value = record.placement
            val offset = index * 7
            placements[offset] = value.x
            placements[offset + 1] = value.y
            placements[offset + 2] = value.z
            placements[offset + 3] = value.qx
            placements[offset + 4] = value.qy
            placements[offset + 5] = value.qz
            placements[offset + 6] = value.qw
        }

        val payload = nativeImportBrepObjects(
            shapeObjects.map { requireNotNull(it.brepFile).absolutePath }.toTypedArray(),
            shapeObjects.map { it.name }.toTypedArray(),
            placements,
            BooleanArray(shapeObjects.size) { shapeObjects[it].visible },
            linearDeflection,
            angularDeflection
        )
        return NativeFreeCadFileScene(
            mesh = payload.toSceneMesh(),
            summary = archiveSummary + "\n" + payload.summary,
            displayName = displayName
        )
    }
}
