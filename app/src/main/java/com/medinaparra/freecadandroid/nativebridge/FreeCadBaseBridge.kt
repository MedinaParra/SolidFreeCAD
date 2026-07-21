package com.medinaparra.freecadandroid.nativebridge

import com.medinaparra.freecadandroid.runtime.BaseRuntimeDescriptor

data class FreeCadBaseHealth(
    val available: Boolean,
    val label: String,
    val diagnostic: String
)

/** Diagnostic bridge compiled against official FreeCAD 1.1.1 src/Base sources. */
object FreeCadBaseBridge {
    private val libraryLoaded: Boolean
        get() = NativeBackendRegistry.coreLoaded

    external fun nativeSelfTest(): String

    fun health(): FreeCadBaseHealth {
        if (!libraryLoaded) {
            return FreeCadBaseHealth(
                available = false,
                label = BaseRuntimeDescriptor.displayName(),
                diagnostic = "No se pudo cargar libfreecad_android_core.so"
            )
        }
        return runCatching { nativeSelfTest() }
            .fold(
                onSuccess = {
                    FreeCadBaseHealth(
                        available = it.contains("PASS", ignoreCase = true),
                        label = BaseRuntimeDescriptor.displayName(),
                        diagnostic = it
                    )
                },
                onFailure = {
                    FreeCadBaseHealth(
                        available = false,
                        label = BaseRuntimeDescriptor.displayName(),
                        diagnostic = it.message ?: it::class.java.simpleName
                    )
                }
            )
    }
}
