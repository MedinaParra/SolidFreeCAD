package com.example.model

import java.util.UUID

sealed class SketchEntity {
    abstract val id: String

    data class Line(
        override val id: String = UUID.randomUUID().toString(),
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val isCenterline: Boolean = false,
        val label: String = ""
    ) : SketchEntity()

    data class Circle(
        override val id: String = UUID.randomUUID().toString(),
        val cx: Float,
        val cy: Float,
        val radius: Float,
        val label: String = ""
    ) : SketchEntity()

    data class Rectangle(
        override val id: String = UUID.randomUUID().toString(),
        val cx: Float,
        val cy: Float,
        val width: Float,
        val height: Float,
        val label: String = ""
    ) : SketchEntity()
}

enum class WorkPlane {
    XY, XZ, YZ
}

enum class OperationType {
    EXTRUDE_BOSS, EXTRUDE_CUT, REVOLVE_BOSS, REVOLVE_CUT, FILLET, SHELL
}

data class CadOperation(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: OperationType,
    val sketchId: String?, // Reference to the sketch
    val depth: Float = 50f, // For extrude boss/cut
    val angle: Float = 360f, // For revolve boss/cut
    val axis: String = "Y-Axis", // For revolve axis: "X-Axis", "Y-Axis"
    val radius: Float = 5f, // For fillet
    val thickness: Float = 2f, // For shell (vaciado)
)

data class CadSketch(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val plane: WorkPlane = WorkPlane.XY,
    val entities: List<SketchEntity> = emptyList()
)

data class ProjectState(
    val projectName: String = "Pieza_SolidMacro",
    val sketches: List<CadSketch> = emptyList(),
    val operations: List<CadOperation> = emptyList(),
    val selectedSketchId: String? = null,
    val selectedOperationId: String? = null,
    val activePlane: WorkPlane = WorkPlane.XY,
    val viewMode3D: Boolean = true,
    // Camera angles
    val yaw: Float = -45f,
    val pitch: Float = 30f,
    val scale: Float = 2.5f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
)
