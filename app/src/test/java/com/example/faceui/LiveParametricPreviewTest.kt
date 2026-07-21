package com.example.faceui

import com.example.nativecad.viewer.NativeSceneMesh
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveParametricPreviewTest {
    @Test
    fun `preview changes diameter and length while preserving topology and base plane`() {
        val source = NativeSceneMesh(
            vertices = floatArrayOf(
                -1f, 0f, 0f, -1f, 0f, 0f,
                 1f, 0f, 0f,  1f, 0f, 0f,
                 1f, 0f, 2f,  1f, 0f, 0f,
                -1f, 0f, 2f, -1f, 0f, 0f
            ),
            indices = intArrayOf(0, 1, 2, 0, 2, 3),
            minX = -1f,
            minY = 0f,
            minZ = 0f,
            maxX = 1f,
            maxY = 0f,
            maxZ = 2f
        )

        val preview = LiveParametricPreview.deformCylinder(
            source = source,
            targetLengthMm = 4f,
            targetDiameterMm = 4f
        )

        assertEquals(-2f, preview.minX, 0.0001f)
        assertEquals(2f, preview.maxX, 0.0001f)
        assertEquals(0f, preview.minZ, 0.0001f)
        assertEquals(4f, preview.maxZ, 0.0001f)
        assertArrayEquals(source.indices, preview.indices)
        assertEquals(-1f, source.minX, 0.0001f)
        assertEquals(2f, source.maxZ, 0.0001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `preview rejects invalid dimensions`() {
        val source = NativeSceneMesh(
            vertices = floatArrayOf(
                0f, 0f, 0f, 0f, 0f, 1f,
                1f, 0f, 0f, 0f, 0f, 1f,
                0f, 1f, 0f, 0f, 0f, 1f
            ),
            indices = intArrayOf(0, 1, 2),
            minX = 0f,
            minY = 0f,
            minZ = 0f,
            maxX = 1f,
            maxY = 1f,
            maxZ = 0f
        )
        LiveParametricPreview.deformCylinder(source, 0f, 10f)
    }
}
