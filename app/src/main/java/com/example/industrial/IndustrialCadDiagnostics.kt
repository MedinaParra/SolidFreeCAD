package com.example.industrial

import android.content.Context
import android.os.Build
import com.example.BuildConfig
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
        buildString {
            appendLine("SolidFreeCAD - diagnóstico de taller")
            appendLine("Versión: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android: ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}")
            appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Runtime FreeCAD: ${BuildConfig.FREECAD_RUNTIME_VERSION}")
            appendLine("Fuente FreeCAD: ${BuildConfig.FREECAD_SOURCE_VERSION}")
            appendLine("Fecha: ${timestamp()}")
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

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
}
