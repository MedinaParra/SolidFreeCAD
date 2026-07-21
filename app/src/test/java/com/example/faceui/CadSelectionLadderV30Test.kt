package com.example.faceui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CadSelectionLadderV30Test {
    private val face = CadViewportFaceSelectionV27("face", intArrayOf(0), floatArrayOf(0f,0f,0f), floatArrayOf(0f,0f,1f), true)
    private val vertex = CadViewportVertexSelectionV27("vertex", 0, floatArrayOf(0f,0f,0f), 2)

    @Test fun repeatedTapCyclesOverlappingCandidates() {
        val ladder = CadSelectionLadderV30()
        val first = ladder.choose(100f, 100f, 10L, listOf(vertex, face))
        val second = ladder.choose(103f, 101f, 300L, listOf(vertex, face))
        assertEquals("vertex", first.selection?.id)
        assertEquals("face", second.selection?.id)
        assertTrue(second.cycled)
    }

    @Test fun distantTapRestartsAtFirstCandidate() {
        val ladder = CadSelectionLadderV30()
        ladder.choose(100f, 100f, 10L, listOf(vertex, face))
        val result = ladder.choose(180f, 100f, 200L, listOf(vertex, face))
        assertEquals("vertex", result.selection?.id)
        assertFalse(result.cycled)
    }
}
