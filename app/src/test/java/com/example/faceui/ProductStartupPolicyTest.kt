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
    fun successfulRendererAutoOpensAgain() {
        assertTrue(ProductStartupPolicy.shouldAutoOpen("L4_surface_ready", forceSafeMode = false))
        assertTrue(ProductStartupPolicy.shouldAutoOpen("PRODUCT_READY", forceSafeMode = false))
    }

    @Test
    fun interruptedLoaderFallsBackToSafeLauncher() {
        assertFalse(ProductStartupPolicy.shouldAutoOpen("P6_before_loader_install", forceSafeMode = false))
        assertFalse(ProductStartupPolicy.shouldAutoOpen("P7_loader_install_returned", forceSafeMode = false))
        assertFalse(ProductStartupPolicy.shouldAutoOpen("L3b_renderer_attached", forceSafeMode = false))
    }

    @Test
    fun explicitSafeModeNeverAutoOpens() {
        assertFalse(ProductStartupPolicy.shouldAutoOpen("L4_surface_ready", forceSafeMode = true))
    }
}
