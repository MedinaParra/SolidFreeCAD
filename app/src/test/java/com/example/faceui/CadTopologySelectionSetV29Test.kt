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

    @Test
    fun mixedSelectionAggregatesLoopPerimeterAndVertexCount() {
        val loop = CadViewportLoopSelectionV27(
            id = "loop-a",
            edgeIds = intArrayOf(1, 2, 3, 4),
            orderedPoints = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f),
            point = floatArrayOf(.5f, 0f, 0f),
            normal = floatArrayOf(0f, 0f, 1f),
            closed = true,
            perimeter = 8f,
            role = CadLoopRoleV29.INNER,
            signedArea = -4f,
            nestingDepth = 1
        )
        val vertex = CadViewportVertexSelectionV27(
            id = "vertex-a",
            vertexIndex = 2,
            point = floatArrayOf(1f, 2f, 3f),
            incidentFeatureEdges = 3
        )

        val selected = CadTopologySelectionSetV29.empty().toggle(loop).toggle(vertex)
        assertEquals(1, selected.loopCount)
        assertEquals(1, selected.vertexCount)
        assertEquals(8f, selected.totalLoopPerimeter, 1e-4f)
        assertEquals(vertex.id, selected.activeId)

        val activated = selected.activate(loop.id)
        assertEquals(loop.id, activated.activeId)
        assertEquals(CadLoopRoleV29.INNER, (activated.active as CadViewportLoopSelectionV27).role)
    }

    @Test(expected = IllegalArgumentException::class)
    fun selectionRejectsMoreThanMobileLimit() {
        CadTopologySelectionSetV29(
            selections = (0..CadTopologySelectionSetV29.MAX_SELECTIONS).map { face("face-$it", 1f) }
        )
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
