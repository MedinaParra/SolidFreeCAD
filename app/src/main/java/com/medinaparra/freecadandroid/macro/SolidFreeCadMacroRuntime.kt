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
        val prepared = prepareSource(sourceCode)
        val installed = PythonRuntimeInstaller.install(context.applicationContext)
        return NativeCadBridge.runPythonMacro(
            pythonHome = installed.home.absolutePath,
            sourceCode = prepared,
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

    internal fun prepareSource(sourceCode: String): String = buildString {
        appendLine(mobileFreeCadCompatibilityPrelude)
        appendLine()
        append(normalizeSource(sourceCode))
    }

    /**
     * The focused Android FreeCAD facade currently implements Rotation(axis, angle)
     * and Rotation(qx, qy, qz, qw), while desktop macros commonly use
     * Rotation(fromVector, toVector). The generated plane-placement macros also
     * call Vector.normalize(). Add those desktop-compatible behaviours once per
     * embedded Python process without replacing the Rotation class itself.
     */
    private val mobileFreeCadCompatibilityPrelude = """
        import math as _solidfreecad_math
        import FreeCAD as _solidfreecad_app

        if not getattr(_solidfreecad_app, '_solidfreecad_vector_rotation_compat', False):
            if not hasattr(_solidfreecad_app.Vector, 'normalize'):
                def _solidfreecad_vector_normalize(self):
                    length = self.Length
                    if length <= 1.0e-15:
                        raise ValueError('Vector must not be null')
                    self.x = self.x / length
                    self.y = self.y / length
                    self.z = self.z / length
                    return length
                _solidfreecad_app.Vector.normalize = _solidfreecad_vector_normalize

            if not hasattr(_solidfreecad_app.Vector, 'sub'):
                def _solidfreecad_vector_sub(self, other):
                    return _solidfreecad_app.Vector(self.x - other.x, self.y - other.y, self.z - other.z)
                _solidfreecad_app.Vector.sub = _solidfreecad_vector_sub

            if not hasattr(_solidfreecad_app.Vector, 'add'):
                def _solidfreecad_vector_add(self, other):
                    return _solidfreecad_app.Vector(self.x + other.x, self.y + other.y, self.z + other.z)
                _solidfreecad_app.Vector.add = _solidfreecad_vector_add

            _solidfreecad_original_rotation_init = _solidfreecad_app.Rotation.__init__

            def _solidfreecad_rotation_init(self, *args):
                if (len(args) == 2 and
                        isinstance(args[0], _solidfreecad_app.Vector) and
                        isinstance(args[1], _solidfreecad_app.Vector)):
                    source = args[0]
                    target = args[1]
                    source_length = source.Length
                    target_length = target.Length
                    if source_length <= 1.0e-15 or target_length <= 1.0e-15:
                        raise ValueError('Rotation vectors must not be null')

                    ax = source.x / source_length
                    ay = source.y / source_length
                    az = source.z / source_length
                    bx = target.x / target_length
                    by = target.y / target_length
                    bz = target.z / target_length
                    dot = max(-1.0, min(1.0, ax * bx + ay * by + az * bz))

                    if dot >= 1.0 - 1.0e-12:
                        return _solidfreecad_original_rotation_init(self, 0.0, 0.0, 0.0, 1.0)

                    if dot <= -1.0 + 1.0e-12:
                        if abs(ax) <= abs(ay) and abs(ax) <= abs(az):
                            qx, qy, qz = 0.0, -az, ay
                        elif abs(ay) <= abs(az):
                            qx, qy, qz = -az, 0.0, ax
                        else:
                            qx, qy, qz = -ay, ax, 0.0
                        length = _solidfreecad_math.sqrt(qx * qx + qy * qy + qz * qz)
                        return _solidfreecad_original_rotation_init(
                            self, qx / length, qy / length, qz / length, 0.0
                        )

                    qx = ay * bz - az * by
                    qy = az * bx - ax * bz
                    qz = ax * by - ay * bx
                    qw = 1.0 + dot
                    length = _solidfreecad_math.sqrt(
                        qx * qx + qy * qy + qz * qz + qw * qw
                    )
                    return _solidfreecad_original_rotation_init(
                        self, qx / length, qy / length, qz / length, qw / length
                    )

                return _solidfreecad_original_rotation_init(self, *args)

            _solidfreecad_app.Rotation.__init__ = _solidfreecad_rotation_init
            _solidfreecad_app._solidfreecad_vector_rotation_compat = True
    """.trimIndent()
}
