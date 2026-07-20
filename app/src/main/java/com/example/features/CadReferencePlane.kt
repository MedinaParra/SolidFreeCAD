package com.example.features

import kotlin.math.abs
import kotlin.math.sqrt

enum class CadPlaneKind {
    DEFAULT_XY,
    DEFAULT_XZ,
    DEFAULT_YZ,
    OFFSET_FROM_PLANE,
    PARALLEL_TO_FACE
}

data class CadVector3(
    val x: Double,
    val y: Double,
    val z: Double
) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite()) { "El vector debe ser finito" }
    }

    val length: Double get() = sqrt(x * x + y * y + z * z)

    fun normalized(): CadVector3 {
        val magnitude = length
        require(magnitude > 1.0e-9) { "La normal del plano no puede ser nula" }
        return CadVector3(x / magnitude, y / magnitude, z / magnitude)
    }

    operator fun plus(other: CadVector3) = CadVector3(x + other.x, y + other.y, z + other.z)
    operator fun times(scale: Double) = CadVector3(x * scale, y * scale, z * scale)
}

data class CadFaceReference(
    val objectId: String,
    val faceId: String,
    val origin: CadVector3,
    val normal: CadVector3
) {
    init {
        require(objectId.isNotBlank())
        require(faceId.isNotBlank())
        normal.normalized()
    }
}

data class CadReferencePlane(
    val id: Long,
    val label: String,
    val kind: CadPlaneKind,
    val origin: CadVector3,
    val normal: CadVector3,
    val parentPlaneId: Long? = null,
    val faceReference: CadFaceReference? = null,
    val offset: Double = 0.0,
    val visible: Boolean = true
) {
    init {
        require(id > 0L)
        require(label.isNotBlank())
        require(offset.isFinite())
        normal.normalized()
        when (kind) {
            CadPlaneKind.DEFAULT_XY,
            CadPlaneKind.DEFAULT_XZ,
            CadPlaneKind.DEFAULT_YZ -> {
                require(parentPlaneId == null)
                require(faceReference == null)
                require(abs(offset) < 1.0e-9)
            }
            CadPlaneKind.OFFSET_FROM_PLANE -> require(parentPlaneId != null)
            CadPlaneKind.PARALLEL_TO_FACE -> require(faceReference != null)
        }
    }

    val normalizedNormal: CadVector3 get() = normal.normalized()
}

data class CadPlaneSet(
    val planes: List<CadReferencePlane> = defaultPlanes()
) {
    init {
        require(planes.map { it.id }.distinct().size == planes.size) { "Los identificadores de plano deben ser únicos" }
        require(planes.count { it.kind == CadPlaneKind.DEFAULT_XY } == 1)
        require(planes.count { it.kind == CadPlaneKind.DEFAULT_XZ } == 1)
        require(planes.count { it.kind == CadPlaneKind.DEFAULT_YZ } == 1)
        val ids = planes.map { it.id }.toSet()
        planes.forEach { plane ->
            plane.parentPlaneId?.let { require(it in ids) { "El plano padre no existe" } }
            require(plane.parentPlaneId != plane.id) { "Un plano no puede depender de sí mismo" }
        }
    }

    fun plane(id: Long): CadReferencePlane = planes.firstOrNull { it.id == id }
        ?: error("Plano $id no encontrado")

    fun createOffset(parentId: Long, offset: Double, label: String? = null): CadPlaneSet {
        require(offset.isFinite())
        val parent = plane(parentId)
        val normal = parent.normalizedNormal
        val nextId = (planes.maxOfOrNull { it.id } ?: 0L) + 1L
        val count = planes.count { it.kind == CadPlaneKind.OFFSET_FROM_PLANE } + 1
        val created = CadReferencePlane(
            id = nextId,
            label = label?.takeIf { it.isNotBlank() } ?: "Plano paralelo$count",
            kind = CadPlaneKind.OFFSET_FROM_PLANE,
            origin = parent.origin + normal * offset,
            normal = normal,
            parentPlaneId = parent.id,
            offset = offset
        )
        return copy(planes = planes + created)
    }

    fun createParallelToFace(reference: CadFaceReference, offset: Double = 0.0, label: String? = null): CadPlaneSet {
        require(offset.isFinite())
        val normal = reference.normal.normalized()
        val nextId = (planes.maxOfOrNull { it.id } ?: 0L) + 1L
        val count = planes.count { it.kind == CadPlaneKind.PARALLEL_TO_FACE } + 1
        val created = CadReferencePlane(
            id = nextId,
            label = label?.takeIf { it.isNotBlank() } ?: "Plano de cara$count",
            kind = CadPlaneKind.PARALLEL_TO_FACE,
            origin = reference.origin + normal * offset,
            normal = normal,
            faceReference = reference,
            offset = offset
        )
        return copy(planes = planes + created)
    }

    fun toggleVisibility(id: Long): CadPlaneSet = copy(
        planes = planes.map { if (it.id == id) it.copy(visible = !it.visible) else it }
    )

    fun remove(id: Long): CadPlaneSet {
        val target = plane(id)
        require(target.kind !in setOf(CadPlaneKind.DEFAULT_XY, CadPlaneKind.DEFAULT_XZ, CadPlaneKind.DEFAULT_YZ)) {
            "Los planos predeterminados no se pueden eliminar"
        }
        require(planes.none { it.parentPlaneId == id }) { "El plano tiene planos derivados" }
        return copy(planes = planes.filterNot { it.id == id })
    }

    companion object {
        fun defaultPlanes(): List<CadReferencePlane> = listOf(
            CadReferencePlane(1L, "Plano XY", CadPlaneKind.DEFAULT_XY, CadVector3(0.0, 0.0, 0.0), CadVector3(0.0, 0.0, 1.0)),
            CadReferencePlane(2L, "Plano XZ", CadPlaneKind.DEFAULT_XZ, CadVector3(0.0, 0.0, 0.0), CadVector3(0.0, 1.0, 0.0)),
            CadReferencePlane(3L, "Plano YZ", CadPlaneKind.DEFAULT_YZ, CadVector3(0.0, 0.0, 0.0), CadVector3(1.0, 0.0, 0.0))
        )
    }
}
