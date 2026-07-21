package com.example.industrial

import android.content.Context
import com.example.features.BasicCadFeature
import com.example.features.BasicCadOperation
import com.example.features.BasicCadProgram
import com.example.features.CadFaceReference
import com.example.features.CadPlaneKind
import com.example.features.CadPlaneSet
import com.example.features.CadReferencePlane
import com.example.features.CadSketch
import com.example.features.CadSketchConstraint
import com.example.features.CadSketchConstraintKind
import com.example.features.CadSketchPrimitive
import com.example.features.CadSketchPrimitiveKind
import com.example.features.CadVector3
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.CRC32

/**
 * Atomic, checksummed recovery store for workshop use.
 *
 * A save is accepted only after a successful BRep rebuild. The previous valid
 * snapshot is kept as a backup so an interrupted write never destroys the
 * last recoverable model.
 */
object IndustrialCadRecoveryStore {
    private const val MAGIC = 0x53464349
    private const val VERSION = 2
    private const val MAX_PAYLOAD_BYTES = 4 * 1024 * 1024
    private const val MAX_ITEMS = 10_000

    data class RecoverySnapshot(
        val program: BasicCadProgram,
        val source: String,
        val savedAtMillis: Long
    )

    fun save(context: Context, program: BasicCadProgram): RecoverySnapshot {
        val directory = directory(context)
        val current = File(directory, "session.sfc-recovery")
        val backup = File(directory, "session.previous.sfc-recovery")
        val temporary = File(directory, "session.tmp")
        val payload = encodePayload(program)
        require(payload.size <= MAX_PAYLOAD_BYTES) { "La sesión supera el límite de recuperación" }
        val checksum = CRC32().apply { update(payload) }.value

        FileOutputStream(temporary).use { fileStream ->
            val buffered = BufferedOutputStream(fileStream)
            val output = DataOutputStream(buffered)
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.writeLong(System.currentTimeMillis())
            output.writeInt(payload.size)
            output.write(payload)
            output.writeLong(checksum)
            output.flush()
            buffered.flush()
            fileStream.fd.sync()
        }

        if (current.exists()) {
            backup.delete()
            if (!current.renameTo(backup)) current.copyTo(backup, overwrite = true)
        }
        if (!temporary.renameTo(current)) {
            temporary.copyTo(current, overwrite = true)
            temporary.delete()
        }
        return RecoverySnapshot(program, "actual", current.lastModified())
    }

    fun loadLatest(context: Context): RecoverySnapshot? {
        val directory = directory(context)
        val candidates = listOf(
            File(directory, "session.sfc-recovery") to "actual",
            File(directory, "session.previous.sfc-recovery") to "respaldo"
        )
        return candidates.firstNotNullOfOrNull { (file, source) ->
            runCatching { decodeFile(file, source) }.getOrNull()
        }
    }

    fun hasRecovery(context: Context): Boolean = loadLatest(context) != null

    fun clear(context: Context) {
        val directory = directory(context)
        listOf("session.sfc-recovery", "session.previous.sfc-recovery", "session.tmp")
            .forEach { File(directory, it).delete() }
    }

    internal fun encodeForTest(program: BasicCadProgram): ByteArray {
        val payload = encodePayload(program)
        val checksum = CRC32().apply { update(payload) }.value
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeLong(1L)
                output.writeInt(payload.size)
                output.write(payload)
                output.writeLong(checksum)
            }
            bytes.toByteArray()
        }
    }

    internal fun decodeForTest(bytes: ByteArray): BasicCadProgram =
        decodeStream(DataInputStream(ByteArrayInputStream(bytes))).program

    private fun directory(context: Context): File =
        File(context.noBackupFilesDir, "industrial-recovery").apply { mkdirs() }

    private fun decodeFile(file: File, source: String): RecoverySnapshot {
        require(file.isFile && file.length() in 1..(MAX_PAYLOAD_BYTES + 128L))
        FileInputStream(file).use { stream ->
            val decoded = decodeStream(DataInputStream(BufferedInputStream(stream)))
            return decoded.copy(source = source, savedAtMillis = file.lastModified().takeIf { it > 0L } ?: decoded.savedAtMillis)
        }
    }

    private fun decodeStream(input: DataInputStream): RecoverySnapshot {
        require(input.readInt() == MAGIC) { "Archivo de recuperación desconocido" }
        val version = input.readInt()
        require(version in 1..VERSION) { "Versión de recuperación no compatible" }
        val savedAt = input.readLong()
        val size = input.readInt()
        require(size in 1..MAX_PAYLOAD_BYTES) { "Tamaño de recuperación inválido" }
        val payload = ByteArray(size)
        input.readFully(payload)
        val expected = input.readLong()
        val actual = CRC32().apply { update(payload) }.value
        require(actual == expected) { "La recuperación está dañada" }
        return RecoverySnapshot(decodePayload(payload, version), "memoria", savedAt)
    }

    private fun encodePayload(program: BasicCadProgram): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeUTF(program.documentName)
            output.writeLong(program.revision)
            output.writeLong(program.activePlaneId)
            output.writeNullableLong(program.activeSketchId)

            output.writeInt(program.planeSet.planes.size)
            program.planeSet.planes.forEach { plane ->
                output.writeLong(plane.id)
                output.writeUTF(plane.label)
                output.writeUTF(plane.kind.name)
                output.writeVector(plane.origin)
                output.writeVector(plane.normal)
                output.writeNullableLong(plane.parentPlaneId)
                output.writeBoolean(plane.faceReference != null)
                plane.faceReference?.let { face ->
                    output.writeUTF(face.objectId)
                    output.writeUTF(face.faceId)
                    output.writeVector(face.origin)
                    output.writeVector(face.normal)
                }
                output.writeDouble(plane.offset)
                output.writeBoolean(plane.visible)
            }

            output.writeInt(program.sketches.size)
            program.sketches.forEach { sketch ->
                output.writeLong(sketch.id)
                output.writeUTF(sketch.label)
                output.writeLong(sketch.planeId)
                output.writeBoolean(sketch.fullyConstrained)
                output.writeBoolean(sketch.visible)
                output.writeInt(sketch.primitives.size)
                sketch.primitives.forEach { primitive ->
                    output.writeLong(primitive.id)
                    output.writeUTF(primitive.kind.name)
                    output.writeParameters(primitive.parameters)
                }
                output.writeInt(sketch.constraints.size)
                sketch.constraints.forEach { constraint ->
                    output.writeLong(constraint.id)
                    output.writeUTF(constraint.kind.name)
                    output.writeLong(constraint.firstPrimitiveId)
                    output.writeNullableLong(constraint.secondPrimitiveId)
                    output.writeInt(constraint.firstPoint)
                    output.writeInt(constraint.secondPoint)
                }
            }

            output.writeInt(program.features.size)
            program.features.forEach { feature ->
                output.writeLong(feature.id)
                output.writeUTF(feature.operation.name)
                output.writeUTF(feature.label)
                output.writeParameters(feature.parameters)
                output.writeBoolean(feature.suppressed)
                output.writeLong(feature.planeId)
                output.writeNullableLong(feature.sketchId)
            }
        }
        bytes.toByteArray()
    }

    private fun decodePayload(payload: ByteArray, version: Int): BasicCadProgram =
        DataInputStream(ByteArrayInputStream(payload)).use { input ->
            val documentName = input.readUTF().requireShortText()
            val revision = input.readLong()
            val activePlaneId = input.readLong()
            val activeSketchId = input.readNullableLong()

            val planes = List(input.readBoundedCount()) {
                val id = input.readLong()
                val label = input.readUTF().requireShortText()
                val kind = enumValueOf<CadPlaneKind>(input.readUTF())
                val origin = input.readVector()
                val normal = input.readVector()
                val parentPlaneId = input.readNullableLong()
                val faceReference = if (input.readBoolean()) {
                    CadFaceReference(
                        input.readUTF().requireShortText(),
                        input.readUTF().requireShortText(),
                        input.readVector(),
                        input.readVector()
                    )
                } else null
                CadReferencePlane(
                    id = id,
                    label = label,
                    kind = kind,
                    origin = origin,
                    normal = normal,
                    parentPlaneId = parentPlaneId,
                    faceReference = faceReference,
                    offset = input.readDouble(),
                    visible = input.readBoolean()
                )
            }

            val sketches = List(input.readBoundedCount()) {
                val id = input.readLong()
                val label = input.readUTF().requireShortText()
                val planeId = input.readLong()
                val fullyConstrained = input.readBoolean()
                val visible = input.readBoolean()
                val primitives = List(input.readBoundedCount()) {
                    CadSketchPrimitive(
                        id = input.readLong(),
                        kind = enumValueOf<CadSketchPrimitiveKind>(input.readUTF()),
                        parameters = input.readParameters()
                    )
                }
                val constraints = if (version >= 2) {
                    List(input.readBoundedCount()) {
                        CadSketchConstraint(
                            id = input.readLong(),
                            kind = enumValueOf<CadSketchConstraintKind>(input.readUTF()),
                            firstPrimitiveId = input.readLong(),
                            secondPrimitiveId = input.readNullableLong(),
                            firstPoint = input.readInt(),
                            secondPoint = input.readInt()
                        )
                    }
                } else emptyList()
                CadSketch(
                    id = id,
                    label = label,
                    planeId = planeId,
                    primitives = primitives,
                    fullyConstrained = fullyConstrained,
                    visible = visible,
                    constraints = constraints
                )
            }

            val features = List(input.readBoundedCount()) {
                BasicCadFeature(
                    id = input.readLong(),
                    operation = enumValueOf<BasicCadOperation>(input.readUTF()),
                    label = input.readUTF().requireShortText(),
                    parameters = input.readParameters(),
                    suppressed = input.readBoolean(),
                    planeId = input.readLong(),
                    sketchId = input.readNullableLong()
                )
            }

            require(input.available() == 0) { "La recuperación contiene datos inesperados" }
            BasicCadProgram(
                documentName = documentName,
                planeSet = CadPlaneSet(planes),
                sketches = sketches,
                activePlaneId = activePlaneId,
                activeSketchId = activeSketchId,
                features = features,
                revision = revision
            )
        }

    private fun DataOutputStream.writeVector(value: CadVector3) {
        writeDouble(value.x)
        writeDouble(value.y)
        writeDouble(value.z)
    }

    private fun DataInputStream.readVector() = CadVector3(readDouble(), readDouble(), readDouble())

    private fun DataOutputStream.writeNullableLong(value: Long?) {
        writeBoolean(value != null)
        if (value != null) writeLong(value)
    }

    private fun DataInputStream.readNullableLong(): Long? = if (readBoolean()) readLong() else null

    private fun DataOutputStream.writeParameters(parameters: Map<String, Double>) {
        writeInt(parameters.size)
        parameters.toSortedMap().forEach { (key, value) ->
            writeUTF(key)
            writeDouble(value)
        }
    }

    private fun DataInputStream.readParameters(): Map<String, Double> {
        val count = readBoundedCount()
        return buildMap(count) {
            repeat(count) {
                val key = readUTF().requireShortText()
                val value = readDouble()
                require(value.isFinite()) { "Parámetro no finito" }
                put(key, value)
            }
        }
    }

    private fun DataInputStream.readBoundedCount(): Int {
        val count = readInt()
        require(count in 0..MAX_ITEMS) { "Cantidad de elementos inválida" }
        return count
    }

    private fun String.requireShortText(): String {
        require(isNotBlank() && length <= 4096) { "Texto de recuperación inválido" }
        return this
    }
}
