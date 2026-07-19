package com.medinaparra.freecadandroid.io

import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

data class FreeCadArchiveContent(
    val displayName: String,
    val documentName: String,
    val brepFiles: List<File>,
    val summary: String
)

/** Extracts OpenCASCADE BREP payloads stored inside a FreeCAD FCStd ZIP. */
object FreeCadArchiveReader {
    private const val maxEntries = 512
    private const val maxSingleEntryBytes = 256L * 1024L * 1024L
    private const val maxTotalBytes = 768L * 1024L * 1024L

    fun extract(
        archiveFile: File,
        outputRoot: File,
        displayName: String
    ): FreeCadArchiveContent {
        require(archiveFile.isFile && archiveFile.length() > 0L) {
            "El documento FCStd seleccionado está vacío"
        }

        val directory = File(outputRoot, archiveFile.nameWithoutExtension).apply {
            deleteRecursively()
            mkdirs()
        }
        val rootPath = directory.canonicalPath + File.separator

        ZipFile(archiveFile).use { zip ->
            val documentEntry = zip.entries().asSequence().firstOrNull {
                it.name.equals("Document.xml", ignoreCase = true)
            } ?: error("Falta Document.xml; el archivo no es un FCStd compatible")

            val documentXml = zip.getInputStream(documentEntry)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

            val documentName = Regex(
                "<Document\\b[^>]*?\\bName=\"([^\"]+)\"",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).find(documentXml)?.groupValues?.getOrNull(1)
                ?: archiveFile.nameWithoutExtension

            val objectMatches = Regex(
                "<Object\\b[^>]*?type=\"([^\"]+)\"[^>]*?name=\"([^\"]+)\"",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).findAll(documentXml).toList()

            val brepEntries = zip.entries().asSequence()
                .filter { !it.isDirectory && isBrep(it.name) }
                .take(maxEntries + 1)
                .toList()
            require(brepEntries.isNotEmpty()) {
                "El documento FCStd no contiene geometría .brp o .brep"
            }
            require(brepEntries.size <= maxEntries) {
                "El documento FCStd contiene demasiados objetos BREP para esta versión móvil"
            }

            var expandedBytes = 0L
            val extracted = brepEntries.mapIndexed { index, entry ->
                require(entry.size < 0L || entry.size <= maxSingleEntryBytes) {
                    "El objeto ${entry.name} supera el límite móvil"
                }
                if (entry.size > 0L) {
                    expandedBytes += entry.size
                    require(expandedBytes <= maxTotalBytes) {
                        "La geometría expandida supera el límite de memoria móvil"
                    }
                }

                val safeName = File(entry.name).name
                    .replace(Regex("[^A-Za-z0-9._-]"), "_")
                    .ifBlank { "Shape_$index.brp" }
                val target = File(directory, "${index}_$safeName")
                require(target.canonicalPath.startsWith(rootPath)) {
                    "Ruta inválida dentro del archivo FCStd"
                }
                zip.getInputStream(entry).use { input ->
                    target.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                require(target.length() > 0L) { "El objeto ${entry.name} está vacío" }
                target
            }

            val typeCounts = objectMatches
                .map { it.groupValues[1] }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(8)
                .joinToString { "${it.key}: ${it.value}" }

            val summary = buildString {
                appendLine("Documento FreeCAD nativo")
                appendLine("Documento: $documentName")
                appendLine("Archivo: $displayName")
                appendLine("Objetos descritos: ${objectMatches.size}")
                appendLine("Geometrías BREP: ${extracted.size}")
                if (typeCounts.isNotBlank()) append("Tipos: $typeCounts")
            }.trimEnd()

            return FreeCadArchiveContent(
                displayName = displayName,
                documentName = documentName,
                brepFiles = extracted,
                summary = summary
            )
        }
    }

    private fun isBrep(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.endsWith(".brp") || lower.endsWith(".brep")
    }
}
