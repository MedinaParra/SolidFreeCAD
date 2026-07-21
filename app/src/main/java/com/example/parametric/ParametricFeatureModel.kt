package com.example.parametric

import kotlin.math.max

/** Semantic output of a sketch. Construction geometry never creates material. */
enum class SketchGeometryMode(val label: String) {
    PROFILE("Perfil cerrado"),
    SURFACE("Superficie plana"),
    CONSTRUCTION("Construcción")
}

data class CircleSketchFeature(
    val id: String = "Sketch001",
    val label: String = "Sketch001",
    val plane: String = "Plano XY",
    val centerX: Double = 0.0,
    val centerY: Double = 0.0,
    val diameterMm: Double = 34.93,
    val geometryMode: SketchGeometryMode = SketchGeometryMode.PROFILE,
    val visible: Boolean = true
) {
    init {
        require(diameterMm.isFinite() && diameterMm > 0.0) {
            "El diámetro del sketch debe ser positivo"
        }
    }
}

data class ExtrusionFeature(
    val id: String = "Extrusion001",
    val label: String = "Extrusión001",
    val sketchId: String = "Sketch001",
    val lengthMm: Double = 40.0,
    val reversed: Boolean = false,
    val visible: Boolean = true
) {
    init {
        require(lengthMm.isFinite() && lengthMm > 0.0) {
            "La longitud de extrusión debe ser positiva"
        }
    }
}

data class ParametricFeatureState(
    val documentName: String = "Pieza1.FCStd",
    val bodyName: String = "Cuerpo1",
    val sketch: CircleSketchFeature = CircleSketchFeature(),
    val extrusion: ExtrusionFeature = ExtrusionFeature(),
    val revision: Long = 0L
) {
    val canCreateSolid: Boolean
        get() = sketch.geometryMode != SketchGeometryMode.CONSTRUCTION

    /** Direct drag of the cylindrical side face modifies the source sketch. */
    fun resizeFromSideFace(diameterMm: Double): ParametricFeatureState = copy(
        sketch = sketch.copy(diameterMm = diameterMm.coerceIn(MIN_DIAMETER_MM, MAX_DIAMETER_MM)),
        revision = revision + 1
    )

    /** Direct drag of the terminal planar face modifies only the extrusion feature. */
    fun resizeFromTopFace(lengthMm: Double): ParametricFeatureState = copy(
        extrusion = extrusion.copy(lengthMm = lengthMm.coerceIn(MIN_LENGTH_MM, MAX_LENGTH_MM)),
        revision = revision + 1
    )

    /** Explicit sketch editing preserves the sketch and extrusion identities. */
    fun editSketch(
        diameterMm: Double,
        geometryMode: SketchGeometryMode
    ): ParametricFeatureState = copy(
        sketch = sketch.copy(
            diameterMm = diameterMm.coerceIn(MIN_DIAMETER_MM, MAX_DIAMETER_MM),
            geometryMode = geometryMode
        ),
        revision = revision + 1
    )

    fun toggleExtrusionDirection(): ParametricFeatureState = copy(
        extrusion = extrusion.copy(reversed = !extrusion.reversed),
        revision = revision + 1
    )

    fun safeDiameter(): Double = max(sketch.diameterMm, MIN_DIAMETER_MM)
    fun safeLength(): Double = max(extrusion.lengthMm, MIN_LENGTH_MM)

    companion object {
        const val MIN_DIAMETER_MM = 1.0
        const val MAX_DIAMETER_MM = 5000.0
        const val MIN_LENGTH_MM = 0.5
        const val MAX_LENGTH_MM = 5000.0
    }
}

enum class ParametricSelection {
    DOCUMENT,
    BODY,
    ORIGIN,
    PLANE_XY,
    PLANE_XZ,
    PLANE_YZ,
    EXTRUSION,
    SKETCH,
    IMPORTED_GEOMETRY
}

enum class InteractionMode {
    SELECT,
    DIRECT_DRAG,
    EDIT_SKETCH
}

enum class DirectDragTarget {
    TOP_FACE,
    SIDE_FACE
}
