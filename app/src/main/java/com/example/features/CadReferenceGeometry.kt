package com.example.features

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Cartesian reference geometry used by sketches and feature placement. */
enum class CadPrincipalPlane(val label: String) {
    XY("Plano XY"),
    XZ("Plano XZ"),
    YZ("Plano YZ")
}

enum class CadReferencePlaneKind {
    PRINCIPAL,
    OFFSET,
    FACE_PARALLEL
}

data class CadVector3(val x: Double, val y: Double, val z: Double) {
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
        require(magnitude.isFinite() && magnitude > 1.0e-9) { "El vector no puede ser nulo" }
        return this * (1.0 / magnitude)
    }

    fun almostParallel(other: CadVector3, tolerance: Double = 1.0e-6): Boolean =
        abs(normalized().dot(other.normalized())) >= 1.0 - tolerance
}

data class CadFaceFrame(
    val reference: String,
    val label: String,
    val origin: CadVector3,
    val normal: CadVector3,
    val xAxis: CadVector3,
    val yAxis: CadVector3
) {
    fun normalized(): CadFaceFrame {
        val n = normal.normalized()
        var x = xAxis - n * xAxis.dot(n)
        if (x.length() <= 1.0e-9) {
            val helper = if (abs(n.z) < 0.9) CadVector3(0.0, 0.0, 1.0) else CadVector3(0.0, 1.0, 0.0)
            x = helper.cross(n)
        }
        x = x.normalized()
        val y = n.cross(x).normalized()
        return copy(normal = n, xAxis = x, yAxis = y)
    }
}

data class CadReferencePlane(
    val id: Long,
    val label: String,
    val kind: CadReferencePlaneKind,
    val origin: CadVector3,
    val normal: CadVector3,
    val xAxis: CadVector3,
    val yAxis: CadVector3,
    val principal: CadPrincipalPlane? = null,
    val sourcePlaneId: Long? = null,
    val sourceFaceReference: String? = null,
    val offsetMm: Double = 0.0,
    val visible: Boolean = false
) {
    init {
        require(id > 0L)
        require(label.isNotBlank())
        require(offsetMm.isFinite())
        require(normal.length() > 1.0e-9)
        require(xAxis.length() > 1.0e-9)
        require(yAxis.length() > 1.0e-9)
    }

    fun frame(): CadFaceFrame = CadFaceFrame(
        reference = "plane:$id",
        label = label,
        origin = origin,
        normal = normal,
        xAxis = xAxis,
        yAxis = yAxis
    ).normalized()

    fun withOffset(source: CadReferencePlane, offset: Double): CadReferencePlane {
        require(offset.isFinite())
        val sourceFrame = source.frame()
        return copy(
            origin = sourceFrame.origin + sourceFrame.normal * offset,
            normal = sourceFrame.normal,
            xAxis = sourceFrame.xAxis,
            yAxis = sourceFrame.yAxis,
            offsetMm = offset,
            sourcePlaneId = source.id
        )
    }

    companion object {
        const val XY_ID = 1L
        const val XZ_ID = 2L
        const val YZ_ID = 3L

        fun defaults(): List<CadReferencePlane> = listOf(
            CadReferencePlane(
                id = XY_ID,
                label = CadPrincipalPlane.XY.label,
                kind = CadReferencePlaneKind.PRINCIPAL,
                origin = CadVector3(0.0, 0.0, 0.0),
                normal = CadVector3(0.0, 0.0, 1.0),
                xAxis = CadVector3(1.0, 0.0, 0.0),
                yAxis = CadVector3(0.0, 1.0, 0.0),
                principal = CadPrincipalPlane.XY
            ),
            CadReferencePlane(
                id = XZ_ID,
                label = CadPrincipalPlane.XZ.label,
                kind = CadReferencePlaneKind.PRINCIPAL,
                origin = CadVector3(0.0, 0.0, 0.0),
                normal = CadVector3(0.0, -1.0, 0.0),
                xAxis = CadVector3(1.0, 0.0, 0.0),
                yAxis = CadVector3(0.0, 0.0, 1.0),
                principal = CadPrincipalPlane.XZ
            ),
            CadReferencePlane(
                id = YZ_ID,
                label = CadPrincipalPlane.YZ.label,
                kind = CadReferencePlaneKind.PRINCIPAL,
                origin = CadVector3(0.0, 0.0, 0.0),
                normal = CadVector3(1.0, 0.0, 0.0),
                xAxis = CadVector3(0.0, 1.0, 0.0),
                yAxis = CadVector3(0.0, 0.0, 1.0),
                principal = CadPrincipalPlane.YZ
            )
        )
    }
}

enum class CadSketchProfileType(val label: String, val closed: Boolean) {
    CIRCLE("Círculo", true),
    RECTANGLE("Rectángulo", true),
    SLOT("Ranura", true),
    POLYGON("Polígono", true),
    LINE("Línea", false),
    ARC("Arco", false)
}

data class CadSketch(
    val id: Long,
    val label: String,
    val planeId: Long,
    val profileType: CadSketchProfileType,
    val parameters: Map<String, Double>,
    val visible: Boolean = true,
    val fullyDefined: Boolean = false
) {
    init {
        require(id > 0L)
        require(label.isNotBlank())
        require(planeId > 0L)
        require(parameters.values.all { it.isFinite() })
    }

    companion object {
        fun defaultCircle(): CadSketch = CadSketch(
            id = 1L,
            label = "Croquis1",
            planeId = CadReferencePlane.XY_ID,
            profileType = CadSketchProfileType.CIRCLE,
            parameters = mapOf("diameter" to 34.93),
            fullyDefined = true
        )
    }
}

fun CadSketchProfileType.defaultParameters(): Map<String, Double> = when (this) {
    CadSketchProfileType.CIRCLE -> mapOf("diameter" to 30.0)
    CadSketchProfileType.RECTANGLE -> mapOf("width" to 30.0, "height" to 20.0)
    CadSketchProfileType.SLOT -> mapOf("length" to 36.0, "width" to 12.0)
    CadSketchProfileType.POLYGON -> mapOf("diameter" to 30.0, "sides" to 6.0)
    CadSketchProfileType.LINE -> mapOf("length" to 30.0, "angle" to 0.0)
    CadSketchProfileType.ARC -> mapOf("radius" to 15.0, "angle" to 90.0)
}

internal fun CadVector3.pythonVector(): String =
    "App.Vector(${x.pythonNumber()}, ${y.pythonNumber()}, ${z.pythonNumber()})"

internal fun regularPolygonPoints(
    plane: CadReferencePlane,
    diameter: Double,
    sides: Int
): List<CadVector3> {
    require(diameter > 0.0)
    require(sides >= 3)
    val frame = plane.frame()
    val radius = diameter / 2.0
    return (0 until sides).map { index ->
        val angle = 2.0 * Math.PI * index / sides.toDouble()
        frame.origin + frame.xAxis * (cos(angle) * radius) + frame.yAxis * (sin(angle) * radius)
    }
}
