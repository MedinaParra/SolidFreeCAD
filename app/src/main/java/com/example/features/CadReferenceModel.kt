package com.example.features

import kotlin.math.abs
import kotlin.math.sqrt

data class CadVector3(
    val x: Double,
    val y: Double,
    val z: Double
) {
    operator fun plus(other: CadVector3) = CadVector3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: CadVector3) = CadVector3(x - other.x, y - other.y, z - other.z)
    operator fun times(scale: Double) = CadVector3(x * scale, y * scale, z * scale)
    fun dot(other: CadVector3): Double = x * other.x + y * other.y + z * other.z
    fun cross(other: CadVector3) = CadVector3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )
    fun length(): Double = sqrt(dot(this))
    fun normalized(): CadVector3 {
        val magnitude = length()
        require(magnitude.isFinite() && magnitude > 1.0e-9) { "El vector de referencia no puede ser nulo" }
        return this * (1.0 / magnitude)
    }

    companion object {
        val ZERO = CadVector3(0.0, 0.0, 0.0)
        val X = CadVector3(1.0, 0.0, 0.0)
        val Y = CadVector3(0.0, 1.0, 0.0)
        val Z = CadVector3(0.0, 0.0, 1.0)

        fun stableXAxis(normalInput: CadVector3): CadVector3 {
            val normal = normalInput.normalized()
            val candidate = if (abs(normal.z) < 0.92) Z.cross(normal) else X
            return (candidate - normal * candidate.dot(normal)).normalized()
        }
    }
}

enum class CadBasePlane(val label: String) {
    XY("Plano XY"),
    XZ("Plano XZ"),
    YZ("Plano YZ")
}

enum class CadPlaneKind {
    BASE,
    OFFSET,
    FACE_PARALLEL
}

data class CadReferencePlane(
    val id: Long,
    val label: String,
    val kind: CadPlaneKind,
    val basePlane: CadBasePlane? = null,
    val parentPlaneId: Long? = null,
    val supportFaceLabel: String? = null,
    val anchor: CadVector3 = CadVector3.ZERO,
    val normal: CadVector3 = CadVector3.Z,
    val xAxis: CadVector3 = CadVector3.X,
    val offsetMm: Double = 0.0,
    val visible: Boolean = false
) {
    init {
        require(normal.length() > 1.0e-9) { "El plano necesita una normal válida" }
        require(xAxis.length() > 1.0e-9) { "El plano necesita un eje X válido" }
        require(offsetMm.isFinite()) { "La separación del plano debe ser finita" }
    }
}

data class ResolvedCadPlane(
    val id: Long,
    val label: String,
    val origin: CadVector3,
    val normal: CadVector3,
    val xAxis: CadVector3,
    val yAxis: CadVector3,
    val visible: Boolean,
    val kind: CadPlaneKind
)

enum class CadSketchProfile(val label: String) {
    CIRCLE("Círculo"),
    RECTANGLE("Rectángulo")
}

data class CadSketchDefinition(
    val id: Long,
    val label: String,
    val planeId: Long,
    val profile: CadSketchProfile,
    val parameters: Map<String, Double>,
    val visible: Boolean = true
) {
    init {
        require(parameters.values.all { it.isFinite() && it > 0.0 }) {
            "Las dimensiones del croquis deben ser positivas"
        }
    }
}

data class PlanarFaceReference(
    val label: String,
    val point: CadVector3,
    val normal: CadVector3,
    val xAxis: CadVector3
)

fun defaultCadReferencePlanes(): List<CadReferencePlane> = listOf(
    CadReferencePlane(
        id = 1L,
        label = CadBasePlane.XY.label,
        kind = CadPlaneKind.BASE,
        basePlane = CadBasePlane.XY,
        normal = CadVector3.Z,
        xAxis = CadVector3.X
    ),
    CadReferencePlane(
        id = 2L,
        label = CadBasePlane.XZ.label,
        kind = CadPlaneKind.BASE,
        basePlane = CadBasePlane.XZ,
        normal = CadVector3.Y,
        xAxis = CadVector3.X
    ),
    CadReferencePlane(
        id = 3L,
        label = CadBasePlane.YZ.label,
        kind = CadPlaneKind.BASE,
        basePlane = CadBasePlane.YZ,
        normal = CadVector3.X,
        xAxis = CadVector3.Y
    )
)

fun defaultCadSketches(): List<CadSketchDefinition> = listOf(
    CadSketchDefinition(
        id = 1L,
        label = "Croquis1",
        planeId = 1L,
        profile = CadSketchProfile.CIRCLE,
        parameters = mapOf("diameter" to 34.93)
    )
)

fun BasicCadOperation.requiresSketch(): Boolean = when (this) {
    BasicCadOperation.BOSS_EXTRUDE,
    BasicCadOperation.CUT_EXTRUDE,
    BasicCadOperation.BOSS_REVOLVE,
    BasicCadOperation.CUT_REVOLVE,
    BasicCadOperation.BOSS_SWEEP,
    BasicCadOperation.CUT_SWEEP,
    BasicCadOperation.BOSS_LOFT,
    BasicCadOperation.CUT_LOFT,
    BasicCadOperation.RIB -> true
    else -> false
}
