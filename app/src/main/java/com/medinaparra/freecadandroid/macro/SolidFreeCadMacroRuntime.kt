package com.medinaparra.freecadandroid.macro

import android.content.Context
import com.medinaparra.freecadandroid.nativebridge.NativeCadBridge
import com.medinaparra.freecadandroid.nativebridge.NativeMacroScene
import com.medinaparra.freecadandroid.runtime.PythonRuntimeInstaller

/**
 * Single entry point for executing .FCMacro-compatible source inside SolidFreeCAD.
 * It installs the correct ABI runtime once, then delegates geometry creation to JNI.
 */
object SolidFreeCadMacroRuntime {
    fun execute(
        context: Context,
        sourceCode: String,
        linearDeflection: Double = 0.10,
        angularDeflection: Double = 0.30
    ): NativeMacroScene {
        val installed = PythonRuntimeInstaller.install(context.applicationContext)
        return NativeCadBridge.runPythonMacro(
            pythonHome = installed.home.absolutePath,
            sourceCode = sourceCode,
            linearDeflection = linearDeflection,
            angularDeflection = angularDeflection
        )
    }

    fun executeFile(
        context: Context,
        sourceCode: ByteArray,
        linearDeflection: Double = 0.10,
        angularDeflection: Double = 0.30
    ): NativeMacroScene = execute(
        context = context,
        sourceCode = sourceCode.toString(Charsets.UTF_8),
        linearDeflection = linearDeflection,
        angularDeflection = angularDeflection
    )
}
