package com.medinaparra.freecadandroid.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseRuntimeDescriptorTest {
    @Test
    fun prefersFirstCompatibleDeviceAbi() {
        assertEquals(
            "arm64-v8a",
            BaseRuntimeDescriptor.selectAbi(listOf("x86_64", "arm64-v8a", "armeabi-v7a"))
        )
        assertEquals(
            "armeabi-v7a",
            BaseRuntimeDescriptor.selectAbi(listOf("armeabi-v7a", "arm64-v8a"))
        )
    }

    @Test
    fun rejectsUnsupportedAbi() {
        assertNull(BaseRuntimeDescriptor.selectAbi(listOf("x86", "x86_64")))
    }

    @Test
    fun exposesValidatedBaseContract() {
        assertEquals("0.11.0", BaseRuntimeDescriptor.runtimeVersion)
        assertEquals("1.1.1", BaseRuntimeDescriptor.freeCadVersion)
        assertEquals("python-runtime/arm64-v8a.zip", BaseRuntimeDescriptor.pythonAssetPath("arm64-v8a"))
        assertTrue("FreeCAD-style macros" in BaseRuntimeDescriptor.capabilities)
        assertTrue("FCStd object-aware import" in BaseRuntimeDescriptor.capabilities)
        assertTrue("Non-destructive FCStd metadata export" in BaseRuntimeDescriptor.capabilities)
        assertTrue("GPU synchronized face preview" in BaseRuntimeDescriptor.capabilities)
    }
}
