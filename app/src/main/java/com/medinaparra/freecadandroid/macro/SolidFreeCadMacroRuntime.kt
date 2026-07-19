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
    private const val maxMacroBytes = 4 * 1024 * 1024

    fun execute(
        context: Context,
        sourceCode: String,
        linearDeflection: Double = 0.10,
        angularDeflection: Double = 0.30
    ): NativeMacroScene {
        val normalized = normalizeSource(sourceCode)
        val installed = PythonRuntimeInstaller.install(context.applicationContext)
        return NativeCadBridge.runPythonMacro(
            pythonHome = installed.home.absolutePath,
            sourceCode = normalized,
            linearDeflection = linearDeflection,
            angularDeflection = angularDeflection
        )
    }

    fun executeFile(
        context: Context,
        sourceCode: ByteArray,
        linearDeflection: Double = 0.10,
        angularDeflection: Double = 0.30
    ): NativeMacroScene {
        require(sourceCode.isNotEmpty()) { "La macro está vacía" }
        require(sourceCode.size <= maxMacroBytes) {
            "La macro supera el límite móvil de ${maxMacroBytes / (1024 * 1024)} MB"
        }
        return execute(
            context = context,
            sourceCode = decodeSource(sourceCode),
            linearDeflection = linearDeflection,
            angularDeflection = angularDeflection
        )
    }

    internal fun decodeSource(bytes: ByteArray): String {
        val start = when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> 3
            else -> 0
        }
        return bytes.copyOfRange(start, bytes.size).toString(Charsets.UTF_8)
    }

    internal fun normalizeSource(sourceCode: String): String {
        val normalized = sourceCode
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        require('\u0000' !in normalized) { "La macro contiene bytes nulos no válidos" }
        require(normalized.isNotBlank()) { "La macro está vacía" }
        return normalized
    }
}
