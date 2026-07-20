package com.medinaparra.freecadandroid.io

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

data class FcStdPlacement(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
    val qx: Double = 0.0,
    val qy: Double = 0.0,
    val qz: Double = 0.0,
    val qw: Double = 1.0
) {
    init {
        require(listOf(x, y, z, qx, qy, qz, qw).all(Double::isFinite)) {
            "FCStd placement contains a non-finite value"
        }
        require(qx * qx + qy * qy + qz * qz + qw * qw > 1.0e-24) {
            "FCStd placement quaternion is null"
        }
    }
}

data class FcStdObjectRecord(
    val name: String,
    val label: String,
    val typeId: String,
    val shapeEntryName: String?,
    val linkedObjectName: String?,
    val brepFile: File?,
    val visible: Boolean,
    val placement: FcStdPlacement,
    val hasPlacementProperty: Boolean,
    val propertyValues: Map<String, String>,
    val links: List<String>
)

/** Keeps object identity available to the JNI bridge without changing the existing UI API. */
object FcStdImportRegistry {
    private var orderedObjects: List<FcStdObjectRecord> = emptyList()

    @Synchronized
    fun register(objects: List<FcStdObjectRecord>) {
        orderedObjects = objects.filter { it.brepFile != null }
    }

    @Synchronized
    fun resolve(paths: List<String>): List<FcStdObjectRecord> {
        val remaining = orderedObjects.toMutableList()
        return buildList {
            paths.forEach { requested ->
                val canonical = runCatching { File(requested).canonicalPath }.getOrDefault(requested)
                val index = remaining.indexOfFirst { record ->
                    val file = record.brepFile ?: return@indexOfFirst false
                    runCatching { file.canonicalPath }.getOrDefault(file.absolutePath) == canonical
                }
                if (index >= 0) add(remaining.removeAt(index))
            }
        }
    }
}

data class FreeCadArchiveContent(
    val displayName: String,
    val documentName: String,
    val sourceArchive: File,
    val documentXml: ByteArray,
    val guiDocumentXml: ByteArray?,
    val objects: List<FcStdObjectRecord>,
    val summary: String
) {
    val shapeObjects: List<FcStdObjectRecord>
        get() = objects.filter { it.brepFile != null }

    /** Deliberately keeps duplicate paths because App::Link instances need independent placements. */
    val brepFiles: List<File>
        get() = shapeObjects.mapNotNull { it.brepFile }
}

/** Object-aware, bounded and XXE-safe reader for FreeCAD FCStd archives. */
object FreeCadArchiveReader {
    private const val maxArchiveEntries = 4096
    private const val maxShapeObjects = 512
    private const val maxXmlBytes = 16L * 1024L * 1024L
    private const val maxSingleEntryBytes = 128L * 1024L * 1024L
    private const val maxExpandedBytes = 384L * 1024L * 1024L
    private const val maxArchiveBytes = 512L * 1024L * 1024L

    fun extract(archiveFile: File, outputRoot: File, displayName: String): FreeCadArchiveContent {
        require(archiveFile.isFile && archiveFile.length() > 0L) {
            "El documento FCStd seleccionado está vacío"
        }
        require(archiveFile.length() <= maxArchiveBytes) {
            "El documento FCStd supera el límite móvil de 512 MB"
        }

        val directory = File(outputRoot, archiveFile.nameWithoutExtension).apply {
            deleteRecursively()
            check(mkdirs() || isDirectory) { "No se pudo crear el directorio temporal FCStd" }
        }
        val rootPath = directory.canonicalPath + File.separator

        ZipFile(archiveFile).use { zip ->
            val entries = zip.entries().asSequence().take(maxArchiveEntries + 1).toList()
            require(entries.size <= maxArchiveEntries) { "El FCStd contiene demasiadas entradas" }
            val normalized = entries.map { it.name.lowercase(Locale.ROOT) }
            require(normalized.size == normalized.toSet().size) {
                "El FCStd contiene nombres de entrada duplicados"
            }
            val byLowerName = entries.associateBy { it.name.lowercase(Locale.ROOT) }
            val documentEntry = byLowerName["document.xml"]
                ?: error("Falta Document.xml; el archivo no es un FCStd compatible")
            val documentXml = zip.getInputStream(documentEntry).use {
                readBounded(it, maxXmlBytes, "Document.xml")
            }
            val guiDocumentXml = byLowerName["guidocument.xml"]?.let { entry ->
                zip.getInputStream(entry).use { readBounded(it, maxXmlBytes, "GuiDocument.xml") }
            }

            val manifest = parseManifest(documentXml, guiDocumentXml, archiveFile.nameWithoutExtension)
            require(manifest.objects.count { it.shapeEntryName != null } <= maxShapeObjects) {
                "El FCStd contiene demasiados objetos Shape para esta versión móvil"
            }

            val referencedNames = manifest.objects.mapNotNull { it.shapeEntryName }.distinct()
            val shapeEntries = if (referencedNames.isNotEmpty()) {
                referencedNames.mapNotNull { byLowerName[it.lowercase(Locale.ROOT)] }
            } else {
                entries.filter { !it.isDirectory && isBrep(it.name) }
            }
            require(shapeEntries.size <= maxShapeObjects) { "El FCStd contiene demasiadas geometrías BREP" }

            var expandedBytes = 0L
            val extractedByEntry = linkedMapOf<String, File>()
            shapeEntries.distinctBy { it.name.lowercase(Locale.ROOT) }.forEachIndexed { index, entry ->
                require(entry.size < 0L || entry.size <= maxSingleEntryBytes) {
                    "La geometría ${entry.name} supera el límite móvil"
                }
                val safeName = File(entry.name).name
                    .replace(Regex("[^A-Za-z0-9._-]"), "_")
                    .ifBlank { "Shape_$index.brp" }
                val target = File(directory, "${index}_$safeName")
                require(target.canonicalPath.startsWith(rootPath)) { "Ruta insegura dentro del FCStd" }

                val actualBytes = zip.getInputStream(entry).use { input ->
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var entryBytes = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            entryBytes += count
                            require(entryBytes <= maxSingleEntryBytes) {
                                "La geometría ${entry.name} excede el límite de extracción"
                            }
                            require(expandedBytes + entryBytes <= maxExpandedBytes) {
                                "La geometría expandida del FCStd supera el límite móvil"
                            }
                            output.write(buffer, 0, count)
                        }
                        entryBytes
                    }
                }
                expandedBytes += actualBytes
                if (actualBytes > 0L) extractedByEntry[entry.name.lowercase(Locale.ROOT)] = target
                else target.delete()
            }
            require(extractedByEntry.isNotEmpty()) { "El FCStd no contiene geometrías BREP legibles" }

            val objects = if (manifest.objects.any { it.shapeEntryName != null }) {
                manifest.objects.map { record ->
                    record.copy(
                        brepFile = record.shapeEntryName?.let {
                            extractedByEntry[it.lowercase(Locale.ROOT)]
                        }
                    )
                }
            } else {
                extractedByEntry.map { (entryName, file) ->
                    val generatedName = File(entryName).nameWithoutExtension.ifBlank { "Shape" }
                    FcStdObjectRecord(
                        name = generatedName,
                        label = generatedName,
                        typeId = "Part::Feature",
                        shapeEntryName = entryName,
                        linkedObjectName = null,
                        brepFile = file,
                        visible = true,
                        placement = FcStdPlacement(),
                        hasPlacementProperty = false,
                        propertyValues = emptyMap(),
                        links = emptyList()
                    )
                }
            }

            FcStdImportRegistry.register(objects)
            val shapeCount = objects.count { it.brepFile != null }
            val visibleCount = objects.count { it.brepFile != null && it.visible }
            val linkCount = objects.count { it.linkedObjectName != null }
            val typeCounts = objects.groupingBy { it.typeId }.eachCount().entries
                .sortedByDescending { it.value }.take(8)
                .joinToString { "${it.key}: ${it.value}" }
            val summary = buildString {
                appendLine("Documento FreeCAD consciente de objetos · Runtime 0.11")
                appendLine("Documento: ${manifest.documentName}")
                appendLine("Archivo: $displayName")
                manifest.programVersion?.takeIf(String::isNotBlank)?.let {
                    appendLine("Versión FreeCAD: $it")
                }
                appendLine("Objetos descritos: ${objects.size}")
                appendLine("Objetos con BREP: $shapeCount")
                appendLine("BREP visibles: $visibleCount")
                appendLine("Instancias App::Link/XLink: $linkCount")
                if (typeCounts.isNotBlank()) append("Tipos: $typeCounts")
            }.trimEnd()

            return FreeCadArchiveContent(
                displayName = displayName,
                documentName = manifest.documentName,
                sourceArchive = archiveFile,
                documentXml = documentXml,
                guiDocumentXml = guiDocumentXml,
                objects = objects,
                summary = summary
            )
        }
    }

    private data class ParsedManifest(
        val documentName: String,
        val programVersion: String?,
        val objects: List<FcStdObjectRecord>
    )

    private fun parseManifest(
        documentBytes: ByteArray,
        guiDocumentBytes: ByteArray?,
        fallbackName: String
    ): ParsedManifest {
        val document = parseXml(documentBytes, "Document.xml")
        val root = document.documentElement
        val objectTypes = root.directChild("Objects")?.directChildren("Object")
            ?.associate { it.attr("name") to it.attr("type") }.orEmpty()
        val guiVisibility = guiDocumentBytes?.let(::parseGuiVisibility).orEmpty()
        val objectData = root.directChild("ObjectData")?.directChildren("Object").orEmpty()
        require(objectData.size <= maxArchiveEntries) { "Document.xml contiene demasiados objetos" }

        val rawRecords = objectData.map { objectElement ->
            val name = objectElement.attr("name")
            require(name.isNotBlank() && name.length <= 1024) { "Objeto FCStd sin nombre válido" }
            val properties = objectElement.directChild("Properties")?.directChildren("Property").orEmpty()
            val byName = properties.associateBy { it.attr("name") }
            val label = byName["Label"]?.firstElementValue() ?: name
            require(label.length <= 4096) { "La etiqueta FCStd es demasiado extensa" }
            val shapeEntry = byName["Shape"]?.firstDescendant("Part")?.attr("file")
                ?.takeIf(String::isNotBlank)
            val linkedObjectName = byName["LinkedObject"]?.firstDescendant("XLink")?.let { link ->
                link.attr("name").takeIf { it.isNotBlank() && link.attr("file").isBlank() }
            }
            val placementElement = byName["Placement"]?.firstDescendant("PropertyPlacement")
            val placement = placementElement?.let(::parsePlacement) ?: FcStdPlacement()
            val documentVisibility = byName["Visibility"]?.firstDescendant("Bool")
                ?.attr("value")?.toBooleanStrictOrNull()
            val values = properties.mapNotNull { property ->
                val propertyName = property.attr("name").takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                property.firstElementValue()?.let { propertyName to it }
            }.toMap()
            val links = properties.flatMap { property ->
                buildList {
                    property.descendants("Link").mapNotNullTo(this) {
                        it.attr("value").takeIf(String::isNotBlank)
                    }
                    property.descendants("XLink").mapNotNullTo(this) {
                        it.attr("name").takeIf { nameValue ->
                            nameValue.isNotBlank() && it.attr("file").isBlank()
                        }
                    }
                }
            }.distinct()
            FcStdObjectRecord(
                name = name,
                label = label,
                typeId = objectTypes[name] ?: "App::Feature",
                shapeEntryName = shapeEntry,
                linkedObjectName = linkedObjectName,
                brepFile = null,
                visible = guiVisibility[name] ?: documentVisibility ?: true,
                placement = placement,
                hasPlacementProperty = placementElement != null,
                propertyValues = values,
                links = links
            )
        }

        val byObjectName = rawRecords.associateBy { it.name }
        fun resolveShape(record: FcStdObjectRecord, visited: MutableSet<String>): String? {
            record.shapeEntryName?.let { return it }
            if (!visited.add(record.name)) return null
            val target = record.linkedObjectName?.let(byObjectName::get) ?: return null
            return resolveShape(target, visited)
        }
        val records = rawRecords.map { record ->
            if (record.shapeEntryName != null || record.linkedObjectName == null) record
            else record.copy(shapeEntryName = resolveShape(record, linkedSetOf()))
        }
        val documentLabel = root.directChild("Properties")?.directChildren("Property")
            ?.firstOrNull { it.attr("name") == "Label" }?.firstElementValue()
        return ParsedManifest(
            documentName = documentLabel?.takeIf(String::isNotBlank) ?: fallbackName,
            programVersion = root.attr("ProgramVersion").takeIf(String::isNotBlank),
            objects = records
        )
    }

    private fun parseGuiVisibility(bytes: ByteArray): Map<String, Boolean> {
        val root = parseXml(bytes, "GuiDocument.xml").documentElement
        return root.directChild("ViewProviderData")?.directChildren("ViewProvider")
            ?.mapNotNull { provider ->
                val name = provider.attr("name").takeIf(String::isNotBlank) ?: return@mapNotNull null
                val value = provider.directChild("Properties")?.directChildren("Property")
                    ?.firstOrNull { it.attr("name") == "Visibility" }
                    ?.firstDescendant("Bool")?.attr("value")?.toBooleanStrictOrNull()
                    ?: return@mapNotNull null
                name to value
            }?.toMap().orEmpty()
    }

    private fun parsePlacement(element: Element): FcStdPlacement = FcStdPlacement(
        x = element.doubleAttr("Px", 0.0),
        y = element.doubleAttr("Py", 0.0),
        z = element.doubleAttr("Pz", 0.0),
        qx = element.doubleAttr("Q0", 0.0),
        qy = element.doubleAttr("Q1", 0.0),
        qz = element.doubleAttr("Q2", 0.0),
        qw = element.doubleAttr("Q3", 1.0)
    )

    private fun parseXml(bytes: ByteArray, label: String): Document {
        val prefix = bytes.take(4096).toByteArray().toString(Charsets.UTF_8).uppercase(Locale.ROOT)
        require("<!DOCTYPE" !in prefix && "<!ENTITY" !in prefix) {
            "$label contiene una declaración XML prohibida"
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }
        return ByteArrayInputStream(bytes).use { factory.newDocumentBuilder().parse(it) }
    }

    private fun readBounded(input: InputStream, limit: Long, label: String): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "$label supera el límite de análisis móvil" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun Element.attr(name: String): String = getAttribute(name).orEmpty()

    private fun Element.doubleAttr(name: String, default: Double): Double {
        val value = attr(name).takeIf(String::isNotBlank)?.toDoubleOrNull() ?: default
        require(value.isFinite()) { "Atributo FCStd $name no finito" }
        return value
    }

    private fun Element.directChildren(tag: String): List<Element> = buildList {
        val nodes = childNodes
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == tag) add(node as Element)
        }
    }

    private fun Element.directChild(tag: String): Element? = directChildren(tag).firstOrNull()

    private fun Element.firstDescendant(tag: String): Element? {
        val nodes = getElementsByTagName(tag)
        return if (nodes.length > 0) nodes.item(0) as? Element else null
    }

    private fun Element.descendants(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private fun Element.firstElementValue(): String? {
        val nodes = childNodes
        for (index in 0 until nodes.length) {
            val child = nodes.item(index) as? Element ?: continue
            child.attr("value").takeIf(String::isNotBlank)?.let { return it }
        }
        return null
    }

    private fun isBrep(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.endsWith(".brp") || lower.endsWith(".brep")
    }
}
