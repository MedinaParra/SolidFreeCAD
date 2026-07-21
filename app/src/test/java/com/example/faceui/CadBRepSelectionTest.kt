package com.example.faceui

import com.example.nativecad.viewer.NativeSceneMesh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CadBRepSelectionTest {
    @Test
    fun raySelectsConnectedPlanarFaceWithoutCrossingSharpEdge() {
        val topology = CadBRepTopology(cubeMesh())
        val selection = topology.pick(
            floatArrayOf(0.5f, 0.5f, 3f),
            floatArrayOf(0f, 0f, -1f)
        )

        assertNotNull(selection)
        selection!!
        assertTrue(selection.planar)
        assertEquals(2, selection.triangleOrdinals.size)
        assertTrue(selection.normal[2] > 0.99f)
        assertEquals(1f, selection.point[2], 1e-4f)
    }

    @Test
    fun sideRayReturnsDifferentFaceCluster() {
        val topology = CadBRepTopology(cubeMesh())
        val selection = topology.pick(
            floatArrayOf(3f, 0.5f, 0.5f),
            floatArrayOf(-1f, 0f, 0f)
        )

        assertNotNull(selection)
        selection!!
        assertTrue(selection.planar)
        assertEquals(2, selection.triangleOrdinals.size)
        assertTrue(selection.normal[0] > 0.99f)
        assertFalse(selection.triangleOrdinals.contains(0))
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
