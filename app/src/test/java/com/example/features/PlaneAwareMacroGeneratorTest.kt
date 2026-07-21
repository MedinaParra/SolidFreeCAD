package com.example.features

import org.junit.Assert.assertTrue
import org.junit.Test

class PlaneAwareMacroGeneratorTest {
    @Test
    fun offsetPlaneIsWrittenAsFreeCadPlacement() {
        val programWithPlane = BasicCadProgram().createOffsetPlane(1L, 18.0)
        val programWithSketch = programWithPlane.createSketch(
            programWithPlane.activePlaneId,
            CadSketchPrimitiveKind.RECTANGLE
        )
        val program = programWithSketch.append(BasicCadOperation.BOSS_EXTRUDE)
        val source = BasicCadMacroGenerator.generate(program)

        assertTrue(source.contains("def _place(shape"))
        assertTrue(source.contains("App.Placement"))
        assertTrue(source.contains("0.000000000,0.000000000,18.000000000"))
        assertTrue(source.contains("Part.makeBox"))
    }

    @Test
    fun circleSketchProducesCylinderExtrusion() {
        val source = BasicCadMacroGenerator.generate(BasicCadProgram())
        assertTrue(source.contains("Part.makeCylinder"))
        assertTrue(source.contains("34.930000000"))
    }
}
