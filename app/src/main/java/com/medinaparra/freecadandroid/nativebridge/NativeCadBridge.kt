package com.medinaparra.freecadandroid.nativebridge

import com.medinaparra.freecadandroid.model.SceneMesh

data class NativeMeshPayload(
    val vertices: FloatArray,
    val indices: IntArray,
    val bounds: FloatArray
) {
    init {
        require(bounds.size == 6) { "Native bounds must contain six values" }
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

data class NativeCadScene(
    val mesh: SceneMesh,
    val buildInfo: String,
    val documentSummary: String
)

/** JNI facade for the validated OCCT-backed FreeCAD-Native 0.8 document core. */
object NativeCadBridge {
    val isAvailable: Boolean by lazy {
        runCatching {
            System.loadLibrary("freecad_android_core")
            true
        }.getOrDefault(false)
    }

    external fun nativeBuildInfo(): String
    external fun nativeCreateDocument(name: String): Long
    external fun nativeCloseDocument(documentId: Long)
    external fun nativeAddBox(
        documentId: Long,
        name: String,
        length: Double,
        width: Double,
        height: Double
    ): Long
    external fun nativeAddCylinder(
        documentId: Long,
        name: String,
        radius: Double,
        height: Double
    ): Long
    external fun nativeAddSphere(documentId: Long, name: String, radius: Double): Long
    external fun nativeAddCone(
        documentId: Long,
        name: String,
        radius1: Double,
        radius2: Double,
        height: Double
    ): Long
    external fun nativeAddTorus(
        documentId: Long,
        name: String,
        majorRadius: Double,
        minorRadius: Double
    ): Long
    external fun nativeAddFuse(
        documentId: Long,
        name: String,
        leftId: Long,
        rightId: Long
    ): Long
    external fun nativeAddCut(
        documentId: Long,
        name: String,
        leftId: Long,
        rightId: Long
    ): Long
    external fun nativeAddCommon(
        documentId: Long,
        name: String,
        leftId: Long,
        rightId: Long
    ): Long
    external fun nativeSetPlacement(
        documentId: Long,
        objectId: Long,
        x: Double,
        y: Double,
        z: Double,
        qx: Double,
        qy: Double,
        qz: Double,
        qw: Double
    )
    external fun nativeSetVisibility(documentId: Long, objectId: Long, visible: Boolean)
    external fun nativeRecompute(documentId: Long): Boolean
    external fun nativeLastError(documentId: Long): String
    external fun nativeDocumentSummary(documentId: Long): String
    external fun nativeCreateSceneMesh(
        documentId: Long,
        linearDeflection: Double,
        angularDeflection: Double
    ): NativeMeshPayload

    fun createStarterScene(): NativeCadScene {
        check(isAvailable) { "El núcleo FreeCAD-Native 0.8 no está empaquetado en este APK" }
        val documentId = nativeCreateDocument("SolidFreeCAD")
        check(documentId != 0L) { "El núcleo nativo no pudo crear el documento" }
        try {
            nativeAddBox(documentId, "Pieza", 80.0, 50.0, 20.0)
            check(nativeRecompute(documentId)) {
                nativeLastError(documentId).ifBlank { "Error de recomputación OCCT" }
            }
            return NativeCadScene(
                mesh = nativeCreateSceneMesh(documentId, 0.35, 0.30).toSceneMesh(),
                buildInfo = nativeBuildInfo(),
                documentSummary = nativeDocumentSummary(documentId)
            )
        } finally {
            nativeCloseDocument(documentId)
        }
    }
}
