package com.example.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BasicCadMacroGeneratorTest {
    @Test
    fun catalogContainsSolidWorksBasicFamilies() {
        val operations = BasicCadOperation.entries.toSet()
        assertTrue(BasicCadOperation.BOSS_EXTRUDE in operations)
        assertTrue(BasicCadOperation.CUT_EXTRUDE in operations)
        assertTrue(BasicCadOperation.BOSS_REVOLVE in operations)
        assertTrue(BasicCadOperation.BOSS_SWEEP in operations)
        assertTrue(BasicCadOperation.BOSS_LOFT in operations)
        assertTrue(BasicCadOperation.FILLET in operations)
        assertTrue(BasicCadOperation.CHAMFER in operations)
        assertTrue(BasicCadOperation.SHELL in operations)
        assertTrue(BasicCadOperation.LINEAR_PATTERN in operations)
        assertTrue(BasicCadOperation.CIRCULAR_PATTERN in operations)
        assertTrue(BasicCadOperation.MIRROR in operations)
        assertTrue(BasicCadOperation.COMBINE_ADD in operations)
        assertTrue(operations.size >= 20)
    }

    @Test
    fun generatedMacroContainsSemanticOperations() {
        val program = BasicCadProgram()
            .append(BasicCadOperation.SIMPLE_HOLE)
            .append(BasicCadOperation.LINEAR_PATTERN)
            .append(BasicCadOperation.SHELL)
        val source = BasicCadMacroGenerator.generate(program)
        assertTrue(source.contains("import FreeCAD as App"))
        assertTrue(source.contains("Part.makeCylinder"))
        assertTrue(source.contains(".cut("))
        assertTrue(source.contains("for i in range"))
        assertTrue(source.contains("Part.show(body, 'Resultado')"))
        assertFalse(source.contains("NaN"))
    }

    @Test
    fun featureParametersRemainEditableAndSuppressible() {
        val appended = BasicCadProgram().append(BasicCadOperation.CUT_EXTRUDE)
        val target = appended.features.last()
        val edited = appended.updateFeature(target.id, mapOf("length" to 20.0, "width" to 7.0, "depth" to 60.0))
        assertEquals(20.0, edited.features.last().parameters.getValue("length"), 0.0)
        val suppressed = edited.toggleSuppressed(target.id)
        assertTrue(suppressed.features.last().suppressed)
        val source = BasicCadMacroGenerator.generate(suppressed)
        assertFalse(source.contains(target.label + ":"))
    }
}
