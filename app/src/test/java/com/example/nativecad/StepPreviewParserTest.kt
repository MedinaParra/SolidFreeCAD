package com.example.nativecad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class StepPreviewParserTest {
    @Test
    fun parsesAp242TriangulatedFaceSet() {
        val step = """
            ISO-10303-21;
            DATA;
            #10=CARTESIAN_POINT_LIST_3D('',((0.,0.,0.),(10.,0.,0.),(0.,10.,0.)));
            #20=TRIANGULATED_FACE_SET('',#10,$,.T.,((1,2,3)));
            ENDSEC;
            END-ISO-10303-21;
        """.trimIndent()

        val preview = StepPreviewParser.parse(step)

        assertEquals("Teselación STEP AP242", preview.mode)
        assertNotNull(preview.mesh)
        assertEquals(3, preview.mesh?.vertexCount)
        assertEquals(1, preview.mesh?.triangleCount)
    }

    @Test
    fun createsEnvelopeWhenStepHasOnlyCartesianPoints() {
        val step = """
            ISO-10303-21;
            DATA;
            #1=CARTESIAN_POINT('',(0.,0.,0.));
            #2=CARTESIAN_POINT('',(100.,50.,25.));
            ENDSEC;
            END-ISO-10303-21;
        """.trimIndent()

        val preview = StepPreviewParser.parse(step)

        assertEquals("Envolvente de puntos STEP", preview.mode)
        assertNotNull(preview.mesh)
        assertEquals(24, preview.mesh?.vertexCount)
        assertEquals(12, preview.mesh?.triangleCount)
        assertEquals(100f, preview.mesh?.maxX)
        assertEquals(50f, preview.mesh?.maxY)
        assertEquals(25f, preview.mesh?.maxZ)
    }
}
