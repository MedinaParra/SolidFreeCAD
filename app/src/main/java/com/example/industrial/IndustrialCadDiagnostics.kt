package com.example.industrial

import android.content.Context
import android.os.Build
import com.example.BuildConfig
import com.medinaparra.freecadandroid.nativebridge.FreeCadBaseBridge
import com.medinaparra.freecadandroid.nativebridge.NativeBackendRegistry
import com.medinaparra.freecadandroid.nativebridge.NativeCadBridge
import com.medinaparra.freecadandroid.nativebridge.NativeFreeCadFileBridge
import com.medinaparra.freecadandroid.nativebridge.NativeStepBridge
import com.medinaparra.freecadandroid.runtime.BaseRuntimeDescriptor
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local-only diagnostics suitable for offline industrial environments. */
object IndustrialCadDiagnostics {
    private const val MAX_LOG_BYTES = 256 * 1024L
    private const val MAX_TECHNICAL_CHARS = 12_000
    private val lock = Any()

    data class Failure(
        val code: String,
        val userMessage: String,
        val technicalMessage: String
    )

    fun event(context: Context, category: String, message: String) {
        append(context, "INFO", category, message, null)
    }

    fun failure(context: Context, operation: String, throwable: Throwable): Failure {
        val root = generateSequence(throwable) { it.cause }.last()
        val raw = buildString {
            append(root::class.java.name)
            root.message?.let { append(": ").append(it) }
            append('\n').append(stackTrace(throwable))
        }.take(MAX_TECHNICAL_CHARS)
        val code = diagnosticCode(operation, root)
        val friendly = friendlyMessage(operation, raw)
        append(context, "ERROR", operation, "$code | $friendly", raw)
        return Failure(code, friendly, raw)
    }

    fun exportReport(context: Context): String = synchronized(lock) {
        val selectedAbi = BaseRuntimeDescriptor.selectAbi(Build.SUPPORTED_ABIS.toList())
        val nativeDirectory = File(context.applicationInfo.nativeLibraryDir.orEmpty())
        val coreLibrary = File(nativeDirectory, "lib${NativeBackendRegistry.CORE_LIBRARY}.so")
        val fcStdLibrary = File(nativeDirectory, "lib${NativeBackendRegistry.FCSTD_LIBRARY}.so")
        val occtLibrary = File(nativeDirectory, "libTKBRep.so")
        val stepLibrary = File(nativeDirectory, "libTKDESTEP.so")
        val pythonLibrary = File(nativeDirectory, "libpython3.14.so")
        val pythonAssetReady = selectedAbi?.let { abi ->
            runCatching {
                context.assets.open(BaseRuntimeDescriptor.pythonAssetPath(abi)).use { true }
            }.getOrDefault(false)
        } ?: false
        val baseHealth = FreeCadBaseBridge.health()
        val nativeBuildInfo = if (NativeCadBridge.isAvailable) {
            runCatching { NativeCadBridge.nativeBuildInfo() }
                .getOrElse { "unavailable: ${it.message ?: it::class.java.simpleName}" }
        } else {
            "native core not loaded"
        }
        val runtime = Runtime.getRuntime()

        buildString {
            appendLine("SolidFreeCAD - diagnóstico de taller")
            appendLine("Versión: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Commit SolidFreeCAD: ${BuildConfig.SOLIDFREECAD_COMMIT}")
            appendLine("FreeCAD Base: ${BuildConfig.FREECAD_SOURCE_VERSION} / runtime ${BuildConfig.FREECAD_RUNTIME_VERSION}")
            appendLine("Commit FreeCAD-Native: ${BuildConfig.FREECAD_NATIVE_COMMIT}")
            appendLine("OpenCASCADE: ${BuildConfig.OCCT_VERSION}")
            appendLine("CPython: ${BuildConfig.CPYTHON_VERSION}")
            appendLine("Backend declarado: ${BuildConfig.CAD_BACKEND_NAME}")
            appendLine("Backend cargado: ${NativeBackendRegistry.activeBackend}")
            appendLine("Android: ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}")
            appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("ABI dispositivo: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("ABI activa: ${selectedAbi ?: "no compatible"}")
            appendLine("Fecha: ${timestamp()}")
            appendLine()
            appendLine("--- AUTODIAGNÓSTICO LOCAL ---")
            NativeBackendRegistry.diagnosticLines().forEach(::appendLine)
            appendLine("Core ELF presente: ${yesNo(coreLibrary.isFile)}")
            appendLine("FCStd ELF presente: ${yesNo(fcStdLibrary.isFile)}")
            appendLine("OpenCASCADE TKBRep presente: ${yesNo(occtLibrary.isFile)}")
            appendLine("STEP TKDESTEP presente: ${yesNo(stepLibrary.isFile)}")
            appendLine("CPython ELF presente: ${yesNo(pythonLibrary.isFile)}")
            appendLine("Python assets presentes: ${yesNo(pythonAssetReady)}")
            appendLine("Puente STEP disponible: ${yesNo(NativeStepBridge.isAvailable && stepLibrary.isFile)}")
            appendLine("Puente FCStd disponible: ${yesNo(NativeFreeCadFileBridge.isAvailable && fcStdLibrary.isFile)}")
            appendLine("FreeCAD Base self-test: ${if (baseHealth.available) "PASS" else "FAIL"} · ${baseHealth.diagnostic}")
            appendLine("Build info nativo: $nativeBuildInfo")
            appendLine("Memoria usada aproximada: ${mb(runtime.totalMemory() - runtime.freeMemory())} MB")
            appendLine("Memoria asignada: ${mb(runtime.totalMemory())} MB")
            appendLine("Memoria máxima JVM: ${mb(runtime.maxMemory())} MB")
            appendLine("Ruta nativa: ${nativeDirectory.absolutePath}")
            appendLine("Ruta de recursos: ${context.filesDir.absolutePath}")
            appendLine("Ruta de recuperación: ${context.noBackupFilesDir.absolutePath}")
            appendLine()
            appendLine("--- EVENTOS ---")
            val directory = directory(context)
            listOf(File(directory, "diagnostics.previous.log"), File(directory, "diagnostics.log"))
                .filter { it.isFile }
                .forEach { append(it.readText()).append('\n') }
        }
    }

    fun clear(context: Context) = synchronized(lock) {
        val directory = directory(context)
        File(directory, "diagnostics.log").delete()
        File(directory, "diagnostics.previous.log").delete()
    }

    private fun append(context: Context, level: String, category: String, message: String, technical: String?) = synchronized(lock) {
        val directory = directory(context)
        val current = File(directory, "diagnostics.log")
        if (current.length() > MAX_LOG_BYTES) {
            val previous = File(directory, "diagnostics.previous.log")
            previous.delete()
            current.renameTo(previous)
        }
        current.appendText(buildString {
            append(timestamp()).append(" | ").append(level).append(" | ").append(category).append(" | ").appendLine(message.replace('\n', ' '))
            technical?.lineSequence()?.take(80)?.forEach { append("    ").appendLine(it) }
        })
    }

    private fun friendlyMessage(operation: String, raw: String): String {
        val text = raw.lowercase(Locale.ROOT)
        return when {
            "rotation angle" in text || "app.rotation" in text ->
                "No se pudo orientar la operación en el plano seleccionado. El último modelo válido se conserva."
            "requiere un croquis" in text || "requires a sketch" in text ->
                "Seleccione o cree un croquis válido antes de ejecutar esta operación."
            "requiere un cuerpo" in text || "requires a body" in text || "cuerpo activo" in text ->
                "Esta operación necesita un cuerpo sólido activo."
            "formato no compatible" in text || "unsupported" in text ->
                "El archivo no es compatible. Use STEP, FCStd o FCMacro."
            "permission" in text || "denied" in text || "eacces" in text ->
                "Android no permitió acceder al archivo. Vuelva a seleccionarlo y conceda permiso."
            "outofmemory" in text || "out of memory" in text || "cannot allocate" in text ->
                "El modelo supera la memoria disponible del teléfono. Cierre otras aplicaciones o simplifique la geometría."
            "damaged" in text || "corrupt" in text || "crc" in text ->
                "El archivo parece estar incompleto o dañado."
            "la extrusión requiere un croquis cerrado" in text ->
                "La extrusión necesita un perfil cerrado; una línea o arco abierto no forma una región."
            else -> "FreeCAD no pudo completar «$operation». El último modelo válido se mantiene sin cambios."
        }
    }

    private fun diagnosticCode(operation: String, root: Throwable): String {
        val source = "$operation|${root::class.java.name}|${root.message.orEmpty()}"
        return "CAD-${source.hashCode().toUInt().toString(16).uppercase(Locale.ROOT).takeLast(6).padStart(6, '0')}"
    }

    private fun stackTrace(error: Throwable): String = StringWriter().also { writer ->
        error.printStackTrace(PrintWriter(writer))
    }.toString()

    private fun directory(context: Context): File =
        File(context.noBackupFilesDir, "industrial-diagnostics").apply { mkdirs() }

    private fun yesNo(value: Boolean): String = if (value) "sí" else "no"

    private fun mb(bytes: Long): Long = bytes / (1024L * 1024L)

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
}
