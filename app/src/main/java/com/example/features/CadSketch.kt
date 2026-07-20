package com.example.features

enum class CadSketchPrimitiveKind {
    CIRCLE,
    RECTANGLE,
    LINE,
    ARC,
    POLYGON
}

data class CadSketchPrimitive(
    val id: Long,
    val kind: CadSketchPrimitiveKind,
    val parameters: Map<String, Double>
) {
    init {
        require(id > 0L)
        require(parameters.values.all { it.isFinite() })
    }
}

data class CadSketch(
    val id: Long,
    val label: String,
    val planeId: Long,
    val primitives: List<CadSketchPrimitive> = emptyList(),
    val fullyConstrained: Boolean = false,
    val visible: Boolean = true
) {
    init {
        require(id > 0L)
        require(label.isNotBlank())
        require(planeId > 0L)
    }

    fun addPrimitive(kind: CadSketchPrimitiveKind, parameters: Map<String, Double>): CadSketch {
        val nextId = (primitives.maxOfOrNull { it.id } ?: 0L) + 1L
        return copy(primitives = primitives + CadSketchPrimitive(nextId, kind, parameters))
    }
}

fun defaultCircleSketch(planeId: Long = 1L): CadSketch = CadSketch(
    id = 1L,
    label = "Croquis1",
    planeId = planeId,
    primitives = listOf(
        CadSketchPrimitive(
            id = 1L,
            kind = CadSketchPrimitiveKind.CIRCLE,
            parameters = mapOf("centerX" to 0.0, "centerY" to 0.0, "diameter" to 34.93)
        )
    ),
    fullyConstrained = true
)
