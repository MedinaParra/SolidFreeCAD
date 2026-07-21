package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraNavigationTest {

    @Test
    fun normalizeYawWrapsPositiveAngles() {
        assertEquals(-170f, normalizeYaw(190f), 0.001f)
        assertEquals(0f, normalizeYaw(360f), 0.001f)
    }

    @Test
    fun normalizeYawWrapsNegativeAngles() {
        assertEquals(170f, normalizeYaw(-190f), 0.001f)
        assertEquals(0f, normalizeYaw(-720f), 0.001f)
    }

    @Test
    fun normalizeYawKeepsAnglesInsideRange() {
        assertEquals(-45f, normalizeYaw(-45f), 0.001f)
        assertEquals(82f, normalizeYaw(82f), 0.001f)
    }
}
