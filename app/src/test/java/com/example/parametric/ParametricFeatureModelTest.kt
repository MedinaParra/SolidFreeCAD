package com.example.parametric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParametricFeatureModelTest {
    @Test
    fun topFaceDragChangesOnlyExtrusionLength() {
        val original = ParametricFeatureState()
        val updated = original.resizeFromTopFace(85.0)

        assertEquals(original.sketch.id, updated.sketch.id)
        assertEquals(original.sketch.diameterMm, updated.sketch.diameterMm, 0.0)
        assertEquals(original.extrusion.id, updated.extrusion.id)
        assertEquals(85.0, updated.extrusion.lengthMm, 0.0)
        assertEquals(original.revision + 1, updated.revision)
    }

    @Test
    fun sideFaceDragChangesSourceSketchDiameter() {
        val original = ParametricFeatureState()
        val updated = original.resizeFromSideFace(62.5)

        assertEquals(original.sketch.id, updated.sketch.id)
        assertEquals(62.5, updated.sketch.diameterMm, 0.0)
        assertEquals(original.extrusion.id, updated.extrusion.id)
        assertEquals(original.extrusion.lengthMm, updated.extrusion.lengthMm, 0.0)
        assertTrue(updated.canCreateSolid)
    }

    @Test
    fun explicitSketchEditPreservesFeatureIdentity() {
        val original = ParametricFeatureState()
        val updated = original.editSketch(48.0, SketchGeometryMode.SURFACE)

        assertEquals("Sketch001", updated.sketch.id)
        assertEquals("Extrusion001", updated.extrusion.id)
        assertEquals(48.0, updated.sketch.diameterMm, 0.0)
        assertEquals(SketchGeometryMode.SURFACE, updated.sketch.geometryMode)
    }

    @Test
    fun constructionGeometryDoesNotCreateMaterial() {
        val state = ParametricFeatureState().editSketch(30.0, SketchGeometryMode.CONSTRUCTION)
        assertTrue(!state.canCreateSolid)
    }
}
