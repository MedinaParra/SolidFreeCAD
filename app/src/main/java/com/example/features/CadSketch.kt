package com.example.features

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class CadSketchPrimitiveKind {
    CIRCLE,
    RECTANGLE,
    LINE,
    ARC,
    POLYGON
}

enum class CadSketchConstraintKind {
    HORIZONTAL,
    VERTICAL,
    COINCIDENT,
    EQUAL_LENGTH,
    EQUAL_RADIUS,
    FIXED
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

    val closedRegion: Boolean
        get() = when (kind) {
            CadSketchPrimitiveKind.CIRCLE,
            CadSketchPrimitiveKind.RECTANGLE,
            CadSketchPrimitiveKind.POLYGON -> true
            CadSketchPrimitiveKind.ARC,
            CadSketchPrimitiveKind.LINE -> false
        }
}

data class CadSketchConstraint(
    val id: Long,
    val kind: CadSketchConstraintKind,
    val firstPrimitiveId: Long,
    val secondPrimitiveId: Long? = null,
    val firstPoint: Int = 0,
    val secondPoint: Int = 0
) {
    init {
        require(id > 0L)
        require(firstPrimitiveId > 0L)
        require(secondPrimitiveId == null || secondPrimitiveId > 0L)
        require(firstPoint in 0..1 && secondPoint in 0..1)
        if (kind in setOf(CadSketchConstraintKind.COINCIDENT, CadSketchConstraintKind.EQUAL_LENGTH, CadSketchConstraintKind.EQUAL_RADIUS)) {
            require(secondPrimitiveId != null) { "$kind requiere dos entidades" }
        }
    }
}

data class CadSketch(
    val id: Long,
    val label: String,
    val planeId: Long,
    val primitives: List<CadSketchPrimitive> = emptyList(),
    val fullyConstrained: Boolean = false,
    val visible: Boolean = true,
    val constraints: List<CadSketchConstraint> = emptyList()
) {
    init {
        require(id > 0L)
        require(label.isNotBlank())
        require(planeId > 0L)
        require(primitives.map { it.id }.distinct().size == primitives.size) { "Los identificadores de entidad deben ser únicos" }
        require(constraints.map { it.id }.distinct().size == constraints.size) { "Los identificadores de restricción deben ser únicos" }
        val primitiveIds = primitives.map { it.id }.toSet()
        constraints.forEach { constraint ->
            require(constraint.firstPrimitiveId in primitiveIds) { "La primera entidad de la restricción no existe" }
            constraint.secondPrimitiveId?.let { require(it in primitiveIds) { "La segunda entidad de la restricción no existe" } }
        }
    }

    val closedPrimitives: List<CadSketchPrimitive> get() = primitives.filter { it.closedRegion }
    val openPrimitives: List<CadSketchPrimitive> get() = primitives.filterNot { it.closedRegion }

    fun addPrimitive(kind: CadSketchPrimitiveKind, parameters: Map<String, Double>): CadSketch {
        val nextId = (primitives.maxOfOrNull { it.id } ?: 0L) + 1L
        return copy(primitives = primitives + CadSketchPrimitive(nextId, kind, parameters), fullyConstrained = false)
    }

    fun updatePrimitive(primitiveId: Long, parameters: Map<String, Double>): CadSketch {
        require(parameters.values.all { it.isFinite() })
        require(primitives.any { it.id == primitiveId }) { "Entidad $primitiveId no encontrada" }
        return copy(
            primitives = primitives.map { if (it.id == primitiveId) it.copy(parameters = parameters) else it },
            fullyConstrained = false
        ).solveConstraints()
    }

    fun removePrimitive(primitiveId: Long): CadSketch = copy(
        primitives = primitives.filterNot { it.id == primitiveId },
        constraints = constraints.filterNot { it.firstPrimitiveId == primitiveId || it.secondPrimitiveId == primitiveId },
        fullyConstrained = false
    )

    fun addConstraint(
        kind: CadSketchConstraintKind,
        firstPrimitiveId: Long,
        secondPrimitiveId: Long? = null,
        firstPoint: Int = 0,
        secondPoint: Int = 0
    ): CadSketch {
        val nextId = (constraints.maxOfOrNull { it.id } ?: 0L) + 1L
        val created = CadSketchConstraint(nextId, kind, firstPrimitiveId, secondPrimitiveId, firstPoint, secondPoint)
        return copy(constraints = constraints + created).solveConstraints()
    }

    fun removeConstraint(constraintId: Long): CadSketch = copy(
        constraints = constraints.filterNot { it.id == constraintId },
        fullyConstrained = false
    ).solveConstraints()

    fun solveConstraints(): CadSketch {
        if (constraints.isEmpty() || primitives.isEmpty()) return this
        val mutable = primitives.associateBy { it.id }.toMutableMap()
        val fixed = constraints.filter { it.kind == CadSketchConstraintKind.FIXED }.map { it.firstPrimitiveId }.toSet()

        repeat(8) {
            constraints.forEach { constraint ->
                val first = mutable[constraint.firstPrimitiveId] ?: return@forEach
                when (constraint.kind) {
                    CadSketchConstraintKind.FIXED -> Unit
                    CadSketchConstraintKind.HORIZONTAL -> if (first.kind == CadSketchPrimitiveKind.LINE && first.id !in fixed) {
                        val p = first.parameters
                        mutable[first.id] = first.copy(parameters = p + ("y2" to (p["y1"] ?: 0.0)))
                    }
                    CadSketchConstraintKind.VERTICAL -> if (first.kind == CadSketchPrimitiveKind.LINE && first.id !in fixed) {
                        val p = first.parameters
                        mutable[first.id] = first.copy(parameters = p + ("x2" to (p["x1"] ?: 0.0)))
                    }
                    CadSketchConstraintKind.COINCIDENT -> {
                        val second = constraint.secondPrimitiveId?.let(mutable::get) ?: return@forEach
                        val anchor = endpoint(first, constraint.firstPoint) ?: return@forEach
                        if (second.id !in fixed) mutable[second.id] = withEndpoint(second, constraint.secondPoint, anchor)
                    }
                    CadSketchConstraintKind.EQUAL_LENGTH -> {
                        val second = constraint.secondPrimitiveId?.let(mutable::get) ?: return@forEach
                        if (first.kind == CadSketchPrimitiveKind.LINE && second.kind == CadSketchPrimitiveKind.LINE && second.id !in fixed) {
                            val target = lineLength(first)
                            val p = second.parameters
                            val x1 = p["x1"] ?: 0.0
                            val y1 = p["y1"] ?: 0.0
                            val angle = atan2((p["y2"] ?: y1) - y1, (p["x2"] ?: x1) - x1)
                            mutable[second.id] = second.copy(parameters = p + mapOf(
                                "x2" to x1 + cos(angle) * target,
                                "y2" to y1 + sin(angle) * target
                            ))
                        }
                    }
                    CadSketchConstraintKind.EQUAL_RADIUS -> {
                        val second = constraint.secondPrimitiveId?.let(mutable::get) ?: return@forEach
                        val value = radiusLike(first) ?: return@forEach
                        if (second.id !in fixed) mutable[second.id] = setRadiusLike(second, value)
                    }
                }
            }
        }
        val solved = primitives.map { mutable[it.id] ?: it }
        val constrainedIds = constraints.flatMap { listOfNotNull(it.firstPrimitiveId, it.secondPrimitiveId) }.toSet()
        return copy(
            primitives = solved,
            fullyConstrained = primitives.isNotEmpty() && primitives.all { it.id in fixed || it.id in constrainedIds }
        )
    }

    private fun endpoint(primitive: CadSketchPrimitive, point: Int): Pair<Double, Double>? = when (primitive.kind) {
        CadSketchPrimitiveKind.LINE -> {
            val suffix = if (point == 0) "1" else "2"
            (primitive.parameters["x$suffix"] ?: 0.0) to (primitive.parameters["y$suffix"] ?: 0.0)
        }
        else -> null
    }

    private fun withEndpoint(primitive: CadSketchPrimitive, point: Int, value: Pair<Double, Double>): CadSketchPrimitive = when (primitive.kind) {
        CadSketchPrimitiveKind.LINE -> {
            val suffix = if (point == 0) "1" else "2"
            primitive.copy(parameters = primitive.parameters + mapOf("x$suffix" to value.first, "y$suffix" to value.second))
        }
        else -> primitive
    }

    private fun lineLength(primitive: CadSketchPrimitive): Double {
        val p = primitive.parameters
        return hypot((p["x2"] ?: 0.0) - (p["x1"] ?: 0.0), (p["y2"] ?: 0.0) - (p["y1"] ?: 0.0))
    }

    private fun radiusLike(primitive: CadSketchPrimitive): Double? {
        return when (primitive.kind) {
            CadSketchPrimitiveKind.CIRCLE -> primitive.parameters["diameter"]?.div(2.0)
            CadSketchPrimitiveKind.ARC,
            CadSketchPrimitiveKind.POLYGON -> primitive.parameters["radius"]
            else -> null
        }
    }

    private fun setRadiusLike(primitive: CadSketchPrimitive, radius: Double): CadSketchPrimitive = when (primitive.kind) {
        CadSketchPrimitiveKind.CIRCLE -> primitive.copy(parameters = primitive.parameters + ("diameter" to radius * 2.0))
        CadSketchPrimitiveKind.ARC,
        CadSketchPrimitiveKind.POLYGON -> primitive.copy(parameters = primitive.parameters + ("radius" to radius))
        else -> primitive
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
