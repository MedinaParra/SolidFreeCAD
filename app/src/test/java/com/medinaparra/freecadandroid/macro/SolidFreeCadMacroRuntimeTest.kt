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

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNullBytes() {
        SolidFreeCadMacroRuntime.normalizeSource("import Part\u0000")
    }
}
