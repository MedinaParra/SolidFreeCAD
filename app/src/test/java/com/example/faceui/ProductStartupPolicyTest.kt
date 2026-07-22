package com.example.faceui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductStartupPolicyTest {
    @Test
    fun firstLaunchAutoOpensCad() {
        assertTrue(ProductStartupPolicy.shouldAutoOpen(null, forceSafeMode = false))
    }

    @Test
    fun successfulWorkbenchAutoOpensAgain() {
        assertTrue(ProductStartupPolicy.shouldAutoOpen("L4_surface_ready", forceSafeMode = false))
    }

    @Test
    fun interruptedLoaderFallsBackToSafeLauncher() {
        assertFalse(ProductStartupPolicy.shouldAutoOpen("P6_before_loader_install", forceSafeMode = false))
    }

    @Test
    fun explicitSafeModeNeverAutoOpens() {
        assertFalse(ProductStartupPolicy.shouldAutoOpen("L4_surface_ready", forceSafeMode = true))
    }
}
