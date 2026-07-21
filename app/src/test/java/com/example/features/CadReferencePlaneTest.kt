package com.example.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CadReferencePlaneTest {
    @Test
    fun defaultPlanesUseExpectedCartesianNormals() {
        val set = CadPlaneSet()
        assertEquals(CadVector3(0.0, 0.0, 1.0), set.plane(1L).normalizedNormal)
        assertEquals(CadVector3(0.0, 1.0, 0.0), set.plane(2L).normalizedNormal)
        assertEquals(CadVector3(1.0, 0.0, 0.0), set.plane(3L).normalizedNormal)
    }

    @Test
    fun offsetPlanePreservesNormalAndMovesOrigin() {
        val next = CadPlaneSet().createOffset(1L, 25.0)
        val plane = next.planes.last()
        assertEquals(CadPlaneKind.OFFSET_FROM_PLANE, plane.kind)
        assertEquals(CadVector3(0.0, 0.0, 25.0), plane.origin)
        assertEquals(CadVector3(0.0, 0.0, 1.0), plane.normalizedNormal)
        assertEquals(1L, plane.parentPlaneId)
    }

    @Test
    fun derivedPlaneCanBeParentOfAnotherPlane() {
        val first = CadPlaneSet().createOffset(2L, 12.0)
        val parent = first.planes.last()
        val second = first.createOffset(parent.id, -4.0)
        val child = second.planes.last()
        assertEquals(parent.id, child.parentPlaneId)
        assertEquals(CadVector3(0.0, 8.0, 0.0), child.origin)
    }

    @Test
    fun faceParallelPlaneNormalizesReferenceNormal() {
        val reference = CadFaceReference(
            objectId = "Body",
            faceId = "Face7",
            origin = CadVector3(4.0, 5.0, 6.0),
            normal = CadVector3(0.0, 0.0, 5.0)
        )
        val plane = CadPlaneSet().createParallelToFace(reference, 3.0).planes.last()
        assertEquals(CadPlaneKind.PARALLEL_TO_FACE, plane.kind)
        assertEquals(CadVector3(4.0, 5.0, 9.0), plane.origin)
        assertEquals(CadVector3(0.0, 0.0, 1.0), plane.normalizedNormal)
    }

    @Test
    fun programBindsSketchAndFeatureToActivePlane() {
        val withPlane = BasicCadProgram().createOffsetPlane(1L, 15.0)
        val withSketch = withPlane.createSketch(withPlane.activePlaneId, CadSketchPrimitiveKind.RECTANGLE)
        val withFeature = withSketch.append(BasicCadOperation.BOSS_EXTRUDE)
        val sketch = withFeature.sketches.last()
        val feature = withFeature.features.last()
        assertEquals(withPlane.activePlaneId, sketch.planeId)
        assertEquals(sketch.id, feature.sketchId)
        assertEquals(sketch.planeId, feature.planeId)
        assertTrue(feature.operation.requiresSketch)
    }
}
