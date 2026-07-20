package com.example.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CadReferenceModelTest {
    @Test
    fun defaultPlanesUseExpectedCartesianNormals() {
        val program = BasicCadProgram()
        val xy = program.resolvePlane(1L)
        val xz = program.resolvePlane(2L)
        val yz = program.resolvePlane(3L)

        assertEquals(1.0, xy.normal.z, 1e-9)
        assertEquals(1.0, xz.normal.y, 1e-9)
        assertEquals(1.0, yz.normal.x, 1e-9)
    }

    @Test
    fun chainedOffsetPlanesResolveFromTheirParent() {
        val first = BasicCadProgram().addOffsetPlane(1L, 10.0, "Plano A")
        val firstId = first.planes.last().id
        val second = first.addOffsetPlane(firstId, -3.0, "Plano B")
        val resolved = second.resolvePlane(second.planes.last().id)

        assertEquals(7.0, resolved.origin.z, 1e-9)
        assertEquals(1.0, resolved.normal.z, 1e-9)
    }

    @Test
    fun faceParallelPlaneKeepsFaceOrientationAndOffset() {
        val face = PlanarFaceReference(
            label = "Cara superior",
            point = CadVector3(2.0, 3.0, 4.0),
            normal = CadVector3.Y,
            xAxis = CadVector3.X
        )
        val program = BasicCadProgram().addFaceParallelPlane(face, 5.0)
        val resolved = program.resolvePlane(program.planes.last().id)

        assertEquals(2.0, resolved.origin.x, 1e-9)
        assertEquals(8.0, resolved.origin.y, 1e-9)
        assertEquals(4.0, resolved.origin.z, 1e-9)
        assertEquals(1.0, resolved.normal.y, 1e-9)
    }

    @Test
    fun sketchAndExtrusionRetainPlaneDependency() {
        val withPlane = BasicCadProgram().addOffsetPlane(1L, 12.0)
        val planeId = withPlane.planes.last().id
        val withSketch = withPlane.addSketch(
            planeId = planeId,
            profile = CadSketchProfile.CIRCLE,
            parameters = mapOf("diameter" to 18.0)
        )
        val sketchId = withSketch.sketches.last().id
        val finalProgram = withSketch.append(BasicCadOperation.BOSS_EXTRUDE, sketchId)
        val feature = finalProgram.features.last()
        val source = BasicCadMacroGenerator.generate(finalProgram)

        assertEquals(sketchId, feature.sketchId)
        assertEquals(planeId, feature.planeId)
        assertTrue(source.contains("12.000000000"))
        assertTrue(source.contains("Part.makeCylinder(9.000000000"))
    }

    @Test
    fun linkedSketchCannotBeRemovedWhileFeatureUsesIt() {
        val program = BasicCadProgram()
        val failure = runCatching { program.removeSketch(1L) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }
}
