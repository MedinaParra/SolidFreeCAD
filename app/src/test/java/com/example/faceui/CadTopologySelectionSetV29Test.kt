package com.example.faceui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CadTopologySelectionSetV29Test {
    @Test
    fun toggleAddsRemovesAndTracksActiveSelection() {
        val face = face("face-a", 12f)
        val edge = edge("edge-a", 5f)

        val selected = CadTopologySelectionSetV29.empty().toggle(face).toggle(edge)
        assertEquals(2, selected.size)
        assertEquals(edge.id, selected.active?.id)
        assertEquals(1, selected.faceCount)
        assertEquals(1, selected.edgeCount)
        assertEquals(12f, selected.approximateArea, 1e-4f)
        assertEquals(5f, selected.totalEdgeLength, 1e-4f)

        val removed = selected.toggle(edge)
        assertEquals(1, removed.size)
        assertEquals(face.id, removed.active?.id)
    }

    @Test
    fun replaceCollapsesMultipleSelection() {
        val multiple = CadTopologySelectionSetV29.empty()
            .toggle(face("face-a", 3f))
            .toggle(face("face-b", 4f))
        assertTrue(multiple.isMultiple)

        val collapsed = multiple.replace(multiple.active)
        assertFalse(collapsed.isMultiple)
        assertEquals(1, collapsed.size)
        assertEquals("face-b", collapsed.active?.id)
    }

    private fun face(id: String, area: Float) = CadViewportFaceSelectionV27(
        id = id,
        triangleOrdinals = intArrayOf(0),
        point = floatArrayOf(0f, 0f, 0f),
        normal = floatArrayOf(0f, 0f, 1f),
        planar = true,
        approximateArea = area
    )

    private fun edge(id: String, length: Float) = CadViewportEdgeSelectionV27(
        id = id,
        edgeId = 1,
        vertexA = 0,
        vertexB = 1,
        start = floatArrayOf(0f, 0f, 0f),
        end = floatArrayOf(length, 0f, 0f),
        point = floatArrayOf(length / 2f, 0f, 0f),
        length = length,
        boundary = true,
        sharp = true
    )
}
