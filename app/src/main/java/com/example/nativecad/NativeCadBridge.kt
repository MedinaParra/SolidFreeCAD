package com.example.nativecad

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.nativecad.viewer.NativeSceneMesh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale
import java.util.zip.ZipInputStream

enum class CadFileFormat(val displayName: String) {
    STEP("STEP"),
    FCSTD("FreeCAD FCStd"),
    UNKNOWN("Desconocido")
}

data class NativeCadCapabilities(
    val sourceRepository: String,
    val nativeLibraryLoaded: Boolean,
    val documentBridgeReady: Boolean,
    val stepInspectionReady: Boolean,
    val fcStdInspectionReady: Boolean,
    val openGlPreviewReady: Boolean,
    val stepGeometryReady: Boolean,
    val fcStdParametricReady: Boolean
)

data class NativeDocumentSummary(
    val fileName: String,
    val format: CadFileFormat,
    val sizeBytes: Long,
    val entityCount: Int = 0,
    val objectCount: Int = 0,
    val brepEntryCount: Int = 0,
    val productNames: List<String> = emptyList(),
    val schema: String? = null,
    val units: String? = null,
    val previewMesh: NativeSceneMesh? = null,
    val previewMode: String? = null,
    val previewSourcePointCount: Int = 0,
    val previewTriangleCount: Int = 0,
    val previewTruncated: Boolean = false,
    val notes: List<String> = emptyList()
)

object NativeCadBridge {
    const val SOURCE_REPOSITORY = "MedinaParra/FreeCAD-Native"
    private const val MAX_STEP_PREVIEW_BYTES = 24 * 1024 * 1024

    private val nativeLibraryLoaded: Boolean by lazy {
        runCatching {
            System.loadLibrary("freecad_native")
            true
        }.getOrDefault(false)
    }

    fun capabilities(): NativeCadCapabilities = NativeCadCapabilities(
        sourceRepository = SOURCE_REPOSITORY,
        nativeLibraryLoaded = nativeLibraryLoaded,
        documentBridgeReady = true,
        stepInspectionReady = true,
        fcStdInspectionReady = true,
        openGlPreviewReady = true,
        stepGeometryReady = nativeLibraryLoaded,
        fcStdParametricReady = false
    )

    suspend fun inspect(context: Context, uri: Uri): Result<NativeDocumentSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                val metadata = queryMetadata(context, uri)
                val format = detectFormat(metadata.first)
                when (format) {
                    CadFileFormat.STEP -> inspectStep(context, uri, metadata.first, metadata.second)
                    CadFileFormat.FCSTD -> inspectFcStd(context, uri, metadata.first, metadata.second)
                    CadFileFormat.UNKNOWN -> NativeDocumentSummary(
                        fileName = metadata.first,
                        format = format,
                        sizeBytes = metadata.second,
                        notes = listOf("Formato no reconocido. Seleccione un archivo .step, .stp o .FCStd.")
                    )
                }
            }
        }

    private fun queryMetadata(context: Context, uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment ?: "documento"
        var size = -1L
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        return name to size
    }

    private fun detectFormat(fileName: String): CadFileFormat {
        val lower = fileName.lowercase(Locale.ROOT)
        return when {
            lower.endsWith(".step") || lower.endsWith(".stp") -> CadFileFormat.STEP
            lower.endsWith(".fcstd") -> CadFileFormat.FCSTD
            else -> CadFileFormat.UNKNOWN
        }
    }

    private fun inspectStep(
        context: Context,
        uri: Uri,
        fileName: String,
        sizeBytes: Long
    ): NativeDocumentSummary {
        var entityCount = 0
        var schema: String? = null
        var units: String? = null
        var lineScanTruncated = false
        val products = linkedSetOf<String>()
        val productRegex = Regex("PRODUCT\\s*\\(\\s*'([^']*)'", RegexOption.IGNORE_CASE)
        val schemaRegex = Regex("FILE_SCHEMA\\s*\\(\\s*\\(\\s*'([^']+)'", RegexOption.IGNORE_CASE)
        val maxLines = 250_000

        context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).use { reader ->
                var lineNumber = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    lineNumber++
                    if (lineNumber > maxLines) {
                        lineScanTruncated = true
                        break
                    }
                    val trimmed = line.trimStart()
                    if (trimmed.startsWith("#") && trimmed.contains('=')) entityCount++
                    if (schema == null) schema = schemaRegex.find(line)?.groupValues?.getOrNull(1)
                    if (products.size < 8) {
                        productRegex.find(line)?.groupValues?.getOrNull(1)
                            ?.takeIf { it.isNotBlank() }
                            ?.let(products::add)
                    }
                    if (units == null) {
                        units = when {
                            line.contains("SI_UNIT(.MILLI.,.METRE.)", ignoreCase = true) -> "milímetros"
                            line.contains("SI_UNIT($,.METRE.)", ignoreCase = true) -> "metros"
                            line.contains("INCH", ignoreCase = true) -> "pulgadas"
                            else -> null
                        }
                    }
                }
            }
        } ?: error("No se pudo abrir el archivo STEP")

        val previewBytes = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytesLimited(MAX_STEP_PREVIEW_BYTES)
        } ?: error("No se pudo volver a abrir el archivo STEP para generar la vista previa")
        val preview = StepPreviewParser.parse(previewBytes.toString(Charsets.ISO_8859_1))
        val byteScanTruncated = sizeBytes > MAX_STEP_PREVIEW_BYTES ||
            (sizeBytes < 0L && previewBytes.size >= MAX_STEP_PREVIEW_BYTES)

        val notes = buildList {
            add("Estructura STEP leída localmente sin subir el archivo a Internet.")
            if (lineScanTruncated) add("El conteo se limitó a $maxLines líneas para proteger la memoria del teléfono.")
            when (preview.mode) {
                "Teselación STEP AP242" -> add("Se encontró una teselación STEP y se generó una malla OpenGL desde los triángulos del archivo.")
                "Envolvente de puntos STEP" -> add("El archivo no contiene una teselación compatible; la vista 3D muestra únicamente la envolvente de sus puntos, no el sólido BRep.")
                else -> add("No fue posible generar una vista 3D ligera desde la información STEP disponible.")
            }
            if (preview.truncated || byteScanTruncated) {
                add("La previsualización se limitó para proteger memoria y GPU; el motor OpenCASCADE definitivo deberá procesar el documento completo.")
            }
            if (!nativeLibraryLoaded) {
                add("La geometría BRep editable todavía requiere empaquetar FreeCAD/OpenCASCADE ARM64 desde FreeCAD-Native.")
            }
        }
        return NativeDocumentSummary(
            fileName = fileName,
            format = CadFileFormat.STEP,
            sizeBytes = sizeBytes,
            entityCount = entityCount,
            productNames = products.toList(),
            schema = schema,
            units = units,
            previewMesh = preview.mesh,
            previewMode = preview.mode,
            previewSourcePointCount = preview.sourcePointCount,
            previewTriangleCount = preview.mesh?.triangleCount ?: preview.sourceTriangleCount,
            previewTruncated = preview.truncated || byteScanTruncated,
            notes = notes
        )
    }

    private fun inspectFcStd(
        context: Context,
        uri: Uri,
        fileName: String,
        sizeBytes: Long
    ): NativeDocumentSummary {
        var objectCount = 0
        var brepEntries = 0
        var schema: String? = null
        var hasDocumentXml = false
        var hasGuiDocumentXml = false
        val maxDocumentXmlBytes = 4 * 1024 * 1024

        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val entryName = entry.name
                    val lower = entryName.lowercase(Locale.ROOT)
                    if (lower.endsWith(".brp") || lower.endsWith(".brep")) brepEntries++
                    if (entryName.equals("GuiDocument.xml", ignoreCase = true)) hasGuiDocumentXml = true
                    if (entryName.equals("Document.xml", ignoreCase = true)) {
                        hasDocumentXml = true
                        val bytes = zip.readBytesLimited(maxDocumentXmlBytes)
                        val xml = bytes.toString(Charsets.UTF_8)
                        objectCount = Regex("<Object\\b").findAll(xml).count()
                        schema = Regex("SchemaVersion=\"([^\"]+)\"")
                            .find(xml)
                            ?.groupValues
                            ?.getOrNull(1)
                    }
                    zip.closeEntry()
                }
            }
        } ?: error("No se pudo abrir el archivo FCStd")

        val notes = buildList {
            add("Contenedor FCStd inspeccionado como paquete ZIP local.")
            if (!hasDocumentXml) add("No se encontró Document.xml; el archivo puede estar dañado o usar una estructura inesperada.")
            if (hasGuiDocumentXml) add("Se detectó información visual de FreeCAD (GuiDocument.xml).")
            if (brepEntries > 0) add("Se detectaron $brepEntries recursos BRep candidatos para el cargador nativo.")
            if (!nativeLibraryLoaded) {
                add("Esta versión aún no restaura el árbol paramétrico ni renderiza los BRep con FreeCAD Core.")
            }
        }
        return NativeDocumentSummary(
            fileName = fileName,
            format = CadFileFormat.FCSTD,
            sizeBytes = sizeBytes,
            objectCount = objectCount,
            brepEntryCount = brepEntries,
            schema = schema,
            notes = notes
        )
    }
}

private fun InputStream.readBytesLimited(limit: Int): ByteArray {
    val buffer = ByteArray(8192)
    val output = ByteArrayOutputStream(minOf(limit, 64 * 1024))
    var total = 0
    while (total < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - total))
        if (read <= 0) break
        output.write(buffer, 0, read)
        total += read
    }
    return output.toByteArray()
}
