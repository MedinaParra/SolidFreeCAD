package com.example.features

import org.junit.Assert.assertTrue
import org.junit.Test

class MultiEntitySketchMacroTest {
    @Test
    fun extrusionUsesEveryClosedRegionAndIgnoresOpenConstructionGeometry() {
        val sketch = CadSketch(1L, "Croquis1", 1L)
            .addPrimitive(CadSketchPrimitiveKind.RECTANGLE, mapOf("centerX" to -10.0, "centerY" to 0.0, "width" to 30.0, "height" to 20.0))
            .addPrimitive(CadSketchPrimitiveKind.RECTANGLE, mapOf("centerX" to 10.0, "centerY" to 0.0, "width" to 30.0, "height" to 20.0))
            .addPrimitive(CadSketchPrimitiveKind.CIRCLE, mapOf("centerX" to 0.0, "centerY" to 0.0, "diameter" to 8.0))
            .addPrimitive(CadSketchPrimitiveKind.LINE, mapOf("x1" to -5.0, "y1" to 0.0, "x2" to 5.0, "y2" to 0.0))
        val program = BasicCadProgram(sketches = listOf(sketch))
        val source = BasicCadMacroGenerator.generate(program)

        assertTrue(source.contains("feature_1_region_1 = Part.makeBox"))
        assertTrue(source.contains("feature_1_region_2 = Part.makeBox"))
        assertTrue(source.contains("feature_1_region_3 = Part.makeCylinder"))
        assertTrue(source.contains("feature_1 = _fuse_all([feature_1_region_1,feature_1_region_2,feature_1_region_3])"))
    }
}
