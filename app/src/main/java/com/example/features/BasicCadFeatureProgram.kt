package com.example.features

import java.util.Locale

enum class BasicCadFeatureFamily(val label: String) {
    SKETCH_BASED("Croquis y material"),
    REMOVE_MATERIAL("Cortes y agujeros"),
    DRESS_UP("Acabado"),
    TRANSFORM("Patrones y transformación"),
    BOOLEAN("Cuerpos")
}

enum class BasicCadOperation(
    val label: String,
    val family: BasicCadFeatureFamily,
    val description: String,
    val generalTopology: Boolean = true,
    val requiresSketch: Boolean = false
) {
    BOSS_EXTRUDE("Saliente-Extruir", BasicCadFeatureFamily.SKETCH_BASED, "Añade material desde un perfil cerrado.", requiresSketch = true),
    CUT_EXTRUDE("Corte-Extruir", BasicCadFeatureFamily.REMOVE_MATERIAL, "Elimina material con un perfil extruido.", requiresSketch = true),
    BOSS_REVOLVE("Saliente-Revolución", BasicCadFeatureFamily.SKETCH_BASED, "Añade una geometría de revolución.", requiresSketch = true),
    CUT_REVOLVE("Corte-Revolución", BasicCadFeatureFamily.REMOVE_MATERIAL, "Crea una ranura o corte de revolución.", requiresSketch = true),
    BOSS_SWEEP("Saliente-Barrido", BasicCadFeatureFamily.SKETCH_BASED, "Barre un perfil circular por una trayectoria.", requiresSketch = true),
    CUT_SWEEP("Corte-Barrido", BasicCadFeatureFamily.REMOVE_MATERIAL, "Elimina material siguiendo una trayectoria.", requiresSketch = true),
    BOSS_LOFT("Saliente-Recubrir", BasicCadFeatureFamily.SKETCH_BASED, "Une dos secciones con un sólido de transición.", requiresSketch = true),
    CUT_LOFT("Corte-Recubrir", BasicCadFeatureFamily.REMOVE_MATERIAL, "Elimina una transición entre dos secciones.", requiresSketch = true),
    FILLET("Redondeo", BasicCadFeatureFamily.DRESS_UP, "Redondeo uniforme de aristas en una pieza prismática.", false),
    CHAMFER("Chaflán", BasicCadFeatureFamily.DRESS_UP, "Genera una pieza prismática con esquinas achaflanadas.", false),
    SHELL("Vaciado", BasicCadFeatureFamily.DRESS_UP, "Vacía un sólido y deja espesor de pared.", false),
    DRAFT("Ángulo de salida", BasicCadFeatureFamily.DRESS_UP, "Aplica conicidad a una geometría extruida.", false),
    RIB("Nervio", BasicCadFeatureFamily.SKETCH_BASED, "Añade un refuerzo delgado desde un perfil triangular.", requiresSketch = true),
    SIMPLE_HOLE("Taladro simple", BasicCadFeatureFamily.REMOVE_MATERIAL, "Perfora un agujero cilíndrico."),
    COUNTERBORE_HOLE("Taladro escariado", BasicCadFeatureFamily.REMOVE_MATERIAL, "Perfora agujero y alojamiento cilíndrico."),
    COUNTERSINK_HOLE("Taladro avellanado", BasicCadFeatureFamily.REMOVE_MATERIAL, "Perfora agujero y cono de avellanado."),
    LINEAR_PATTERN("Matriz lineal", BasicCadFeatureFamily.TRANSFORM, "Repite una operación en una dirección."),
    CIRCULAR_PATTERN("Matriz circular", BasicCadFeatureFamily.TRANSFORM, "Repite una operación alrededor de un eje."),
    MIRROR("Simetría", BasicCadFeatureFamily.TRANSFORM, "Crea una copia simétrica respecto de un plano."),
    MOVE_COPY_BODY("Mover/Copiar cuerpo", BasicCadFeatureFamily.TRANSFORM, "Traslada y duplica cuerpos."),
    COMBINE_ADD("Combinar-Sumar", BasicCadFeatureFamily.BOOLEAN, "Fusiona dos cuerpos."),
    COMBINE_SUBTRACT("Combinar-Sustraer", BasicCadFeatureFamily.BOOLEAN, "Resta un cuerpo herramienta."),
    COMBINE_COMMON("Combinar-Común", BasicCadFeatureFamily.BOOLEAN, "Conserva la intersección de dos cuerpos.")
}

data class BasicCadFeature(
    val id: Long,
    val operation: BasicCadOperation,
    val label: String = operation.label,
    val parameters: Map<String, Double> = emptyMap(),
    val suppressed: Boolean = false,
    val planeId: Long = 1L,
    val sketchId: Long? = null
)

data class BasicCadProgram(
    val documentName: String = "Pieza1",
    val planeSet: CadPlaneSet = CadPlaneSet(),
    val sketches: List<CadSketch> = listOf(defaultCircleSketch()),
    val activePlaneId: Long = 1L,
    val activeSketchId: Long? = 1L,
    val features: List<BasicCadFeature> = listOf(
        BasicCadFeature(
            id = 1L,
            operation = BasicCadOperation.BOSS_EXTRUDE,
            label = "Saliente-Extruir1",
            parameters = mapOf("diameter" to 34.93, "depth" to 40.0),
            planeId = 1L,
            sketchId = 1L
        )
    ),
    val revision: Long = 0L
) {
    init {
        planeSet.plane(activePlaneId)
        activeSketchId?.let { id -> require(sketches.any { it.id == id }) { "El croquis activo no existe" } }
        sketches.forEach { planeSet.plane(it.planeId) }
        features.forEach { feature ->
            planeSet.plane(feature.planeId)
            feature.sketchId?.let { sketchId -> require(sketches.any { it.id == sketchId }) { "El croquis de la operación no existe" } }
        }
    }

    fun append(operation: BasicCadOperation): BasicCadProgram = append(operation, activePlaneId, activeSketchId)

    fun append(operation: BasicCadOperation, planeId: Long, sketchId: Long? = activeSketchId): BasicCadProgram {
        planeSet.plane(planeId)
        if (operation.requiresSketch) require(sketchId != null && sketches.any { it.id == sketchId }) {
            "${operation.label} requiere un croquis activo"
        }
        val number = features.count { it.operation == operation } + 1
        val feature = BasicCadFeature(
            id = (features.maxOfOrNull { it.id } ?: 0L) + 1L,
            operation = operation,
            label = "${operation.label}$number",
            parameters = operation.defaultParameters(),
            planeId = planeId,
            sketchId = if (operation.requiresSketch) sketchId else null
        )
        return copy(features = features + feature, revision = revision + 1L)
    }

    fun updateFeature(featureId: Long, parameters: Map<String, Double>): BasicCadProgram {
        require(parameters.values.all { it.isFinite() }) { "Los parámetros deben ser finitos" }
        return copy(
            features = features.map { feature ->
                if (feature.id == featureId) feature.copy(parameters = parameters) else feature
            },
            revision = revision + 1L
        )
    }

    fun remove(featureId: Long): BasicCadProgram = copy(
        features = features.filterNot { it.id == featureId },
        revision = revision + 1L
    )

    fun toggleSuppressed(featureId: Long): BasicCadProgram = copy(
        features = features.map { feature ->
            if (feature.id == featureId) feature.copy(suppressed = !feature.suppressed) else feature
        },
        revision = revision + 1L
    )

    fun selectPlane(planeId: Long): BasicCadProgram {
        planeSet.plane(planeId)
        return copy(activePlaneId = planeId, activeSketchId = null, revision = revision + 1L)
    }

    fun createOffsetPlane(parentPlaneId: Long, offset: Double, label: String? = null): BasicCadProgram {
        val updated = planeSet.createOffset(parentPlaneId, offset, label)
        return copy(planeSet = updated, activePlaneId = updated.planes.last().id, activeSketchId = null, revision = revision + 1L)
    }

    fun createFaceParallelPlane(reference: CadFaceReference, offset: Double = 0.0, label: String? = null): BasicCadProgram {
        val updated = planeSet.createParallelToFace(reference, offset, label)
        return copy(planeSet = updated, activePlaneId = updated.planes.last().id, activeSketchId = null, revision = revision + 1L)
    }

    fun togglePlaneVisibility(planeId: Long): BasicCadProgram = copy(
        planeSet = planeSet.toggleVisibility(planeId),
        revision = revision + 1L
    )

    fun createSketch(planeId: Long = activePlaneId, primitive: CadSketchPrimitiveKind = CadSketchPrimitiveKind.CIRCLE): BasicCadProgram {
        planeSet.plane(planeId)
        val nextId = (sketches.maxOfOrNull { it.id } ?: 0L) + 1L
        val sketch = CadSketch(
            id = nextId,
            label = "Croquis$nextId",
            planeId = planeId
        ).addPrimitive(primitive, primitive.defaultSketchParameters())
        return copy(sketches = sketches + sketch, activePlaneId = planeId, activeSketchId = sketch.id, revision = revision + 1L)
    }

    fun selectSketch(sketchId: Long): BasicCadProgram {
        val sketch = sketches.firstOrNull { it.id == sketchId } ?: error("Croquis $sketchId no encontrado")
        return copy(activeSketchId = sketch.id, activePlaneId = sketch.planeId, revision = revision + 1L)
    }

    fun updateSketchPrimitive(sketchId: Long, primitiveId: Long, parameters: Map<String, Double>): BasicCadProgram {
        require(parameters.values.all { it.isFinite() })
        return updateSketch(sketchId) { it.updatePrimitive(primitiveId, parameters) }
    }

    fun addSketchPrimitive(
        sketchId: Long,
        kind: CadSketchPrimitiveKind,
        parameters: Map<String, Double> = kind.defaultSketchParameters()
    ): BasicCadProgram = updateSketch(sketchId) { it.addPrimitive(kind, parameters) }

    fun removeSketchPrimitive(sketchId: Long, primitiveId: Long): BasicCadProgram =
        updateSketch(sketchId) { it.removePrimitive(primitiveId) }

    fun addSketchConstraint(
        sketchId: Long,
        kind: CadSketchConstraintKind,
        firstPrimitiveId: Long,
        secondPrimitiveId: Long? = null,
        firstPoint: Int = 0,
        secondPoint: Int = 0
    ): BasicCadProgram = updateSketch(sketchId) {
        it.addConstraint(kind, firstPrimitiveId, secondPrimitiveId, firstPoint, secondPoint)
    }

    fun removeSketchConstraint(sketchId: Long, constraintId: Long): BasicCadProgram =
        updateSketch(sketchId) { it.removeConstraint(constraintId) }

    fun replaceSketch(updated: CadSketch): BasicCadProgram {
        require(sketches.any { it.id == updated.id }) { "Croquis ${updated.id} no encontrado" }
        planeSet.plane(updated.planeId)
        return copy(
            sketches = sketches.map { if (it.id == updated.id) updated.solveConstraints() else it },
            activePlaneId = updated.planeId,
            activeSketchId = updated.id,
            revision = revision + 1L
        )
    }

    private fun updateSketch(sketchId: Long, transform: (CadSketch) -> CadSketch): BasicCadProgram {
        require(sketches.any { it.id == sketchId }) { "Croquis $sketchId no encontrado" }
        return copy(
            sketches = sketches.map { if (it.id == sketchId) transform(it) else it },
            activeSketchId = sketchId,
            revision = revision + 1L
        )
    }

    fun updateBaseCylinder(diameter: Double, depth: Double): BasicCadProgram {
        require(diameter.isFinite() && diameter > 0.0)
        require(depth.isFinite() && depth > 0.0)
        val first = features.firstOrNull() ?: return this
        val updatedFeature = first.copy(parameters = first.parameters + mapOf("diameter" to diameter, "depth" to depth))
        val updatedSketches = first.sketchId?.let { sketchId ->
            sketches.map { sketch ->
                if (sketch.id != sketchId) sketch
                else {
                    var updatedCircle = false
                    sketch.copy(primitives = sketch.primitives.map { primitive ->
                        if (!updatedCircle && primitive.kind == CadSketchPrimitiveKind.CIRCLE) {
                            updatedCircle = true
                            primitive.copy(parameters = primitive.parameters + ("diameter" to diameter))
                        } else primitive
                    })
                }
            }
        } ?: sketches
        return copy(features = listOf(updatedFeature) + features.drop(1), sketches = updatedSketches, revision = revision + 1L)
    }
}

fun CadSketchPrimitiveKind.defaultSketchParameters(): Map<String, Double> = when (this) {
    CadSketchPrimitiveKind.CIRCLE -> mapOf("centerX" to 0.0, "centerY" to 0.0, "diameter" to 20.0)
    CadSketchPrimitiveKind.RECTANGLE -> mapOf("centerX" to 0.0, "centerY" to 0.0, "width" to 30.0, "height" to 20.0)
    CadSketchPrimitiveKind.LINE -> mapOf("x1" to -10.0, "y1" to 0.0, "x2" to 10.0, "y2" to 0.0)
    CadSketchPrimitiveKind.ARC -> mapOf("centerX" to 0.0, "centerY" to 0.0, "radius" to 10.0, "startAngle" to 0.0, "endAngle" to 180.0)
    CadSketchPrimitiveKind.POLYGON -> mapOf("centerX" to 0.0, "centerY" to 0.0, "radius" to 12.0, "sides" to 6.0)
}

fun BasicCadOperation.defaultParameters(): Map<String, Double> = when (this) {
    BasicCadOperation.BOSS_EXTRUDE -> mapOf("length" to 30.0, "width" to 22.0, "depth" to 12.0)
    BasicCadOperation.CUT_EXTRUDE -> mapOf("length" to 12.0, "width" to 8.0, "depth" to 50.0)
    BasicCadOperation.BOSS_REVOLVE -> mapOf("majorRadius" to 24.0, "minorRadius" to 4.0)
    BasicCadOperation.CUT_REVOLVE -> mapOf("majorRadius" to 15.0, "minorRadius" to 2.2)
    BasicCadOperation.BOSS_SWEEP -> mapOf("radius" to 2.5, "length" to 38.0, "rise" to 18.0)
    BasicCadOperation.CUT_SWEEP -> mapOf("radius" to 1.8, "length" to 45.0, "rise" to 12.0)
    BasicCadOperation.BOSS_LOFT -> mapOf("radius1" to 12.0, "radius2" to 6.0, "height" to 20.0)
    BasicCadOperation.CUT_LOFT -> mapOf("radius1" to 7.0, "radius2" to 3.0, "height" to 50.0)
    BasicCadOperation.FILLET -> mapOf("length" to 42.0, "width" to 30.0, "height" to 18.0, "radius" to 3.0)
    BasicCadOperation.CHAMFER -> mapOf("length" to 42.0, "width" to 30.0, "height" to 18.0, "distance" to 4.0)
    BasicCadOperation.SHELL -> mapOf("radius" to 18.0, "height" to 32.0, "thickness" to 2.5)
    BasicCadOperation.DRAFT -> mapOf("radius1" to 18.0, "radius2" to 14.0, "height" to 30.0)
    BasicCadOperation.RIB -> mapOf("length" to 28.0, "height" to 18.0, "thickness" to 3.0)
    BasicCadOperation.SIMPLE_HOLE -> mapOf("diameter" to 8.0, "depth" to 80.0)
    BasicCadOperation.COUNTERBORE_HOLE -> mapOf("diameter" to 7.0, "counterboreDiameter" to 13.0, "counterboreDepth" to 5.0)
    BasicCadOperation.COUNTERSINK_HOLE -> mapOf("diameter" to 7.0, "sinkDiameter" to 14.0, "sinkDepth" to 4.0)
    BasicCadOperation.LINEAR_PATTERN -> mapOf("count" to 4.0, "spacing" to 14.0, "diameter" to 5.0)
    BasicCadOperation.CIRCULAR_PATTERN -> mapOf("count" to 6.0, "radius" to 14.0, "diameter" to 4.0)
    BasicCadOperation.MIRROR -> mapOf("offset" to 18.0, "diameter" to 6.0)
    BasicCadOperation.MOVE_COPY_BODY -> mapOf("dx" to 45.0, "dy" to 0.0, "dz" to 0.0)
    BasicCadOperation.COMBINE_ADD -> mapOf("size" to 14.0)
    BasicCadOperation.COMBINE_SUBTRACT -> mapOf("diameter" to 10.0)
    BasicCadOperation.COMBINE_COMMON -> mapOf("diameter" to 28.0)
}

internal fun Double.pythonNumber(): String {
    require(isFinite()) { "CAD parameter must be finite" }
    return String.format(Locale.ROOT, "%.9f", this)
}
