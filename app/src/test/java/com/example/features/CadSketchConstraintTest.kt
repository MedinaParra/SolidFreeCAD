package com.example.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CadSketchConstraintTest {
    @Test
    fun horizontalAndCoincidentConstraintsSolveDeterministically() {
        var sketch = CadSketch(10L, "Croquis taller", 1L)
            .addPrimitive(CadSketchPrimitiveKind.LINE, mapOf("x1" to 0.0, "y1" to 0.0, "x2" to 20.0, "y2" to 4.0))
            .addPrimitive(CadSketchPrimitiveKind.LINE, mapOf("x1" to 30.0, "y1" to 9.0, "x2" to 45.0, "y2" to 22.0))
        sketch = sketch
            .addConstraint(CadSketchConstraintKind.HORIZONTAL, 1L)
            .addConstraint(CadSketchConstraintKind.COINCIDENT, 1L, 2L, firstPoint = 1, secondPoint = 0)

        val first = sketch.primitives.first { it.id == 1L }
        val second = sketch.primitives.first { it.id == 2L }
        assertEquals(first.parameters["y1"], first.parameters["y2"])
        assertEquals(first.parameters["x2"], second.parameters["x1"])
        assertEquals(first.parameters["y2"], second.parameters["y1"])
    }

    @Test
    fun equalRadiusAndRemovalKeepReferencesValid() {
        var sketch = CadSketch(11L, "Taladros", 1L)
            .addPrimitive(CadSketchPrimitiveKind.CIRCLE, mapOf("centerX" to 0.0, "centerY" to 0.0, "diameter" to 12.0))
            .addPrimitive(CadSketchPrimitiveKind.CIRCLE, mapOf("centerX" to 30.0, "centerY" to 0.0, "diameter" to 5.0))
            .addConstraint(CadSketchConstraintKind.EQUAL_RADIUS, 1L, 2L)

        assertEquals(12.0, sketch.primitives.last().parameters["diameter"] ?: 0.0, 0.0)
        sketch = sketch.removePrimitive(1L)
        assertTrue(sketch.constraints.isEmpty())
        assertEquals(1, sketch.primitives.size)
    }
}
