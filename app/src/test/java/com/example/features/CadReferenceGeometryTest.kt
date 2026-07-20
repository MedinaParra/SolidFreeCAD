package com.example.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CadReferenceGeometryTest {
    @Test
    fun defaultPlanesAreOrthogonalAndStable() {
        val planes = CadReferencePlane.defaults()
        assertEquals(listOf(1L, 2L, 3L), planes.map { it.id })
        val xy = planes.first { it.principal == CadPrincipalPlane.XY }
        val xz = planes.first { it.principal == CadPrincipalPlane.XZ }
        val yz = planes.first { it.principal == CadPrincipalPlane.YZ }
        assertEquals(0.0, xy.normal.dot(xz.normal), 1.0e-9)
        assertEquals(0.0, xy.normal.dot(yz.normal), 1.0e-9)
        assertEquals(0.0, xz.normal.dot(yz.normal), 1.0e-9)
    }

    @Test
    fun offsetPlaneKeepsParentOrientation() {
        val program = BasicCadProgram().addOffsetPlane(CadReferencePlane.XY_ID, 25.0)
        val created = program.activePlane()
        assertEquals(CadReferencePlaneKind.OFFSET, created.kind)
        assertEquals(25.0, created.origin.z, 1.0e-9)
        assertEquals(CadReferencePlane.XY_ID, created.sourcePlaneId)
        assertTrue(created.normal.almostParallel(CadVector3(0.0, 0.0, 1.0)))
    }

    @Test
    fun faceParallelPlaneNormalizesCapturedFrame() {
        val frame = CadFaceFrame(
            reference = "triangle:7",
            label = "Cara",
            origin = CadVector3(3.0, 4.0, 5.0),
            normal = CadVector3(0.0, 0.0, 2.0),
            xAxis = CadVector3(4.0, 0.0, 0.0),
            yAxis = CadVector3(0.0, 4.0, 0.0)
        )
        val program = BasicCadProgram().addFaceParallelPlane(frame, 2.0)
        val created = program.activePlane()
        assertEquals(CadReferencePlaneKind.FACE_PARALLEL, created.kind)
        assertEquals(7.0, created.origin.z, 1.0e-9)
        assertEquals(1.0, created.normal.length(), 1.0e-9)
        assertEquals(0.0, created.normal.dot(created.xAxis), 1.0e-9)
    }

    @Test
    fun circleSketchOnOffsetPlaneDrivesExtrusionMacro() {
        val base = BasicCadProgram().addOffsetPlane(CadReferencePlane.XY_ID, 18.0)
        val withSketch = base.addSketch(CadSketchProfileType.CIRCLE, base.activePlaneId, mapOf("diameter" to 22.0))
        val program = withSketch.append(BasicCadOperation.BOSS_EXTRUDE, withSketch.activeSketchId)
        val source = BasicCadMacroGenerator.generate(program)
        assertTrue(source.contains("App.Vector(0.000000000, 0.000000000, 18.000000000)"))
        assertTrue(source.contains("Part.makeCylinder(22.000000000/2.0"))
    }
}
