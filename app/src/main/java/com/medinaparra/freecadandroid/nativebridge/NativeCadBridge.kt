package com.medinaparra.freecadandroid.nativebridge

import com.medinaparra.freecadandroid.model.SceneMesh
import com.medinaparra.freecadandroid.runtime.BaseRuntimeDescriptor

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

data class NativeMacroPayload(
    val vertices: FloatArray,
    val indices: IntArray,
    val bounds: FloatArray,
    val success: Boolean,
    val documentId: Long,
    val output: String,
    val error: String,
    val summary: String
) {
    init {
        require(bounds.size == 6) { "Native bounds must contain six values" }
    }

    fun toSceneMesh(): SceneMesh {
        check(success) { error.ifBlank { "La macro Python no finalizó correctamente" } }
        check(vertices.isNotEmpty() && indices.isNotEmpty()) {
            "La macro no produjo geometría triangulada visible"
        }
        return SceneMesh(
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
}

data class NativeCadScene(
    val mesh: SceneMesh,
    val buildInfo: String,
    val documentSummary: String
)

data class NativeMacroScene(
    val mesh: SceneMesh,
    val pythonVersion: String,
    val output: String,
    val documentSummary: String,
    val documentId: Long
)

/**
 * JNI facade for FreeCAD-Native base runtime 0.9.1.
 *
 * The packaged core contains OpenCASCADE, CPython, STEP, initial FCStd support,
 * official FreeCAD 1.1.1 Base::Vector3d/Matrix4D and FreeCAD-style macro APIs.
 */
object NativeCadBridge {
    val isAvailable: Boolean
        get() = NativeBackendRegistry.coreLoaded

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

    external fun nativeInitializePython(pythonHome: String): String
    external fun nativeRunMacro(
        sourceCode: String,
        linearDeflection: Double,
        angularDeflection: Double
    ): NativeMacroPayload

    fun createStarterScene(): NativeCadScene = createCylinderScene(
        diameterMm = 34.93,
        lengthMm = 40.0,
        documentName = "SolidFreeCAD"
    )

    /** Rebuilds the logical Sketch001 -> Extrusion001 feature from its parameters. */
    fun createCylinderScene(
        diameterMm: Double,
        lengthMm: Double,
        documentName: String = "SolidFreeCAD"
    ): NativeCadScene {
        require(diameterMm.isFinite() && diameterMm > 0.0) { "Diámetro no válido" }
        require(lengthMm.isFinite() && lengthMm > 0.0) { "Longitud no válida" }
        check(isAvailable) {
            "${BaseRuntimeDescriptor.displayName()} no está empaquetado para este dispositivo"
        }

        val documentId = nativeCreateDocument(documentName)
        check(documentId != 0L) { "El núcleo nativo no pudo crear el documento" }
        try {
            val extrusionId = nativeAddCylinder(
                documentId = documentId,
                name = "Extrusion001",
                radius = diameterMm * 0.5,
                height = lengthMm
            )
            check(extrusionId != 0L) { "OCCT no pudo crear Extrusion001" }
            check(nativeRecompute(documentId)) {
                nativeLastError(documentId).ifBlank { "Error de recomputación OCCT" }
            }
            return NativeCadScene(
                mesh = nativeCreateSceneMesh(documentId, 0.25, 0.24).toSceneMesh(),
                buildInfo = nativeBuildInfo(),
                documentSummary = nativeDocumentSummary(documentId)
            )
        } finally {
            nativeCloseDocument(documentId)
        }
    }

    fun runPythonMacro(
        pythonHome: String,
        sourceCode: String,
        linearDeflection: Double = 0.10,
        angularDeflection: Double = 0.30
    ): NativeMacroScene {
        require(sourceCode.isNotBlank()) { "La macro está vacía" }
        check(isAvailable) { "El núcleo nativo no está disponible" }
        val pythonVersion = nativeInitializePython(pythonHome)
        val payload = nativeRunMacro(sourceCode, linearDeflection, angularDeflection)
        check(payload.success) {
            buildString {
                append(payload.error.ifBlank { "La ejecución de la macro falló" })
                if (payload.output.isNotBlank()) {
                    append("\n\nSalida:\n")
                    append(payload.output)
                }
            }
        }
        return NativeMacroScene(
            mesh = payload.toSceneMesh(),
            pythonVersion = pythonVersion,
            output = payload.output,
            documentSummary = payload.summary,
            documentId = payload.documentId
        )
    }
}
