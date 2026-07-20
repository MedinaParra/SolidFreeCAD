package com.example.faceui

import com.example.nativecad.viewer.NativeSceneMesh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CadBRepTopologyV27Test {
    @Test
    fun faceReportsAreaAndBoundaryLoop() {
        val topology = CadBRepTopologyV27(cubeMesh())
        val selection = topology.pickFace(
            floatArrayOf(0.5f, 0.5f, 3f),
            floatArrayOf(0f, 0f, -1f)
        )
        assertNotNull(selection)
        selection!!
        assertEquals(1f, selection.approximateArea, 1e-4f)
        assertEquals(1, selection.boundaryLoopCount)
    }

    @Test
    fun edgeFilterPicksSharpTopEdgeAndReportsLength() {
        val topology = CadBRepTopologyV27(cubeMesh())
        val selection = topology.pick(
            floatArrayOf(0.5f, -0.06f, 3f),
            floatArrayOf(0f, 0f, -1f),
            CadViewportSelectionModeV27.EDGE,
            0.12f
        ) as? CadViewportEdgeSelectionV27
        assertNotNull(selection)
        selection!!
        assertEquals(1f, selection.length, 1e-4f)
        assertTrue(selection.sharp || selection.boundary)
    }

    @Test
    fun vertexFilterPicksFeatureVertex() {
        val topology = CadBRepTopologyV27(cubeMesh())
        val selection = topology.pick(
            floatArrayOf(-0.04f, -0.04f, 3f),
            floatArrayOf(0f, 0f, -1f),
            CadViewportSelectionModeV27.VERTEX,
            0.10f
        ) as? CadViewportVertexSelectionV27
        assertNotNull(selection)
        selection!!
        assertEquals(0f, selection.point[0], 1e-4f)
        assertEquals(0f, selection.point[1], 1e-4f)
        assertEquals(1f, selection.point[2], 1e-4f)
        assertTrue(selection.incidentFeatureEdges >= 3)
    }

    @Test
    fun loopFilterReturnsClosedTopPerimeterWithoutTriangleDiagonal() {
        val topology = CadBRepTopologyV27(cubeMesh())
        val selection = topology.pick(
            floatArrayOf(0.5f, 0.5f, 3f),
            floatArrayOf(0f, 0f, -1f),
            CadViewportSelectionModeV27.LOOP,
            0.1f
        ) as? CadViewportLoopSelectionV27
        assertNotNull(selection)
        selection!!
        assertTrue(selection.closed)
        assertEquals(4, selection.edgeIds.size)
        assertEquals(4f, selection.perimeter, 1e-4f)
        assertEquals(5, selection.orderedPoints.size / 3)
    }

    @Test
    fun autoSelectionPrefersVertexThenEdgeThenFace() {
        val topology = CadBRepTopologyV27(cubeMesh())
        val vertex = topology.pick(
            floatArrayOf(-0.02f, -0.02f, 3f),
            floatArrayOf(0f, 0f, -1f),
            CadViewportSelectionModeV27.AUTO,
            0.1f
        )
        val face = topology.pick(
            floatArrayOf(0.5f, 0.5f, 3f),
            floatArrayOf(0f, 0f, -1f),
            CadViewportSelectionModeV27.AUTO,
            0.05f
        )
        assertTrue(vertex is CadViewportVertexSelectionV27)
        assertTrue(face is CadViewportFaceSelectionV27)
    }

    private fun cubeMesh(): NativeSceneMesh {
        val positions = arrayOf(
            floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 0f, 0f), floatArrayOf(1f, 1f, 0f), floatArrayOf(0f, 1f, 0f),
            floatArrayOf(0f, 0f, 1f), floatArrayOf(1f, 0f, 1f), floatArrayOf(1f, 1f, 1f), floatArrayOf(0f, 1f, 1f)
        )
        val vertices = FloatArray(positions.size * 6)
        positions.forEachIndexed { index, point ->
            val base = index * 6
            vertices[base] = point[0]
            vertices[base + 1] = point[1]
            vertices[base + 2] = point[2]
        }
        val indices = intArrayOf(
            4, 5, 6, 4, 6, 7,
            1, 2, 6, 1, 6, 5,
            0, 3, 2, 0, 2, 1,
            0, 4, 7, 0, 7, 3,
            3, 7, 6, 3, 6, 2,
            0, 1, 5, 0, 5, 4
        )
        return NativeSceneMesh(vertices, indices, 0f, 0f, 0f, 1f, 1f, 1f)
    }
}
