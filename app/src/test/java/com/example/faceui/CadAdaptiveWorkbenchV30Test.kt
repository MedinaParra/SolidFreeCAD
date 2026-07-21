package com.example.faceui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CadAdaptiveWorkbenchV30Test {
    @Test fun phoneUsesSinglePersistentPanelAndSafeTouchTargets() {
        val policy = CadAdaptiveWorkbenchV30.resolve(720f, 412f, gloveMode = false)
        assertEquals(CadWindowClassV30.COMPACT, policy.windowClass)
        assertFalse(policy.allowsTwoPersistentPanels)
        assertTrue(policy.touchTargetDp >= 48)
        assertTrue(policy.selectionLabelSp >= 11)
    }

    @Test fun tabletKeepsBothSidePanels() {
        val policy = CadAdaptiveWorkbenchV30.resolve(1280f, 800f, gloveMode = false)
        assertEquals(CadWindowClassV30.EXPANDED, policy.windowClass)
        assertTrue(policy.allowsTwoPersistentPanels)
        assertTrue(policy.propertiesWidthDp > policy.treeWidthDp)
    }

    @Test fun gloveModeRaisesTargetSize() {
        val normal = CadAdaptiveWorkbenchV30.resolve(900f, 600f, gloveMode = false)
        val glove = CadAdaptiveWorkbenchV30.resolve(900f, 600f, gloveMode = true)
        assertTrue(glove.touchTargetDp > normal.touchTargetDp)
    }
}
