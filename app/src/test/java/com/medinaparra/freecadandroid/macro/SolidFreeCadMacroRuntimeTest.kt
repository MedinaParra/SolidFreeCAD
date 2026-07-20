package com.medinaparra.freecadandroid.macro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SolidFreeCadMacroRuntimeTest {
    @Test
    fun decodesUtf8BomAndNormalizesLineEndings() {
        val payload = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "import FreeCAD as App\r\nimport Part\r\n".toByteArray(Charsets.UTF_8)
        val decoded = SolidFreeCadMacroRuntime.decodeSource(payload)
        val normalized = SolidFreeCadMacroRuntime.normalizeSource(decoded)
        assertEquals("import FreeCAD as App\nimport Part\n", normalized)
        assertFalse(normalized.startsWith("\uFEFF"))
    }

    @Test
    fun acceptsOrdinaryFreeCadMacroSource() {
        val source = """
            import FreeCAD as App
            import Part
            doc = App.newDocument('Test')
            Part.show(Part.makeBox(10, 20, 30))
        """.trimIndent()
        val normalized = SolidFreeCadMacroRuntime.normalizeSource(source)
        assertTrue(normalized.contains("Part.makeBox"))
    }

    @Test
    fun prependsDesktopCompatibleVectorRotationSupport() {
        val prepared = SolidFreeCadMacroRuntime.prepareSource(
            """
                import FreeCAD as App
                normal = App.Vector(0, 1, 0)
                normal.normalize()
                rotation = App.Rotation(App.Vector(0, 0, 1), normal)
            """.trimIndent()
        )

        assertTrue(prepared.contains("Vector.normalize = _solidfreecad_vector_normalize"))
        assertTrue(prepared.contains("Rotation.__init__ = _solidfreecad_rotation_init"))
        assertTrue(prepared.contains("rotation = App.Rotation(App.Vector(0, 0, 1), normal)"))
        assertTrue(prepared.indexOf("_solidfreecad_vector_rotation_compat") < prepared.indexOf("rotation = App.Rotation"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNullBytes() {
        SolidFreeCadMacroRuntime.normalizeSource("import Part\u0000")
    }
}
