package com.example.faceui

import com.medinaparra.freecadandroid.nativebridge.NativeStepSessionSnapshot

data class CadNativeFaceMetadataV30(
    val faceId: Int,
    val point: FloatArray,
    val normal: FloatArray,
    val area: Float,
    val planar: Boolean
)

data class CadNativeTopologySnapshotV30(
    val revision: Int,
    val triangleFaceIds: IntArray,
    val faces: Map<Int, CadNativeFaceMetadataV30>
) {
    init {
        require(revision >= 0)
        require(triangleFaceIds.all { it > 0 }) { "Native OCCT face identifiers are 1-based" }
    }
}

internal fun NativeStepSessionSnapshot.toCadTopologyV30(): CadNativeTopologySnapshotV30 =
    CadNativeTopologySnapshotV30(
        revision = revision,
        triangleFaceIds = triangleFaceIds.copyOf(),
        faces = faces.associate { face ->
            face.faceId to CadNativeFaceMetadataV30(
                faceId = face.faceId,
                point = face.point.copyOf(),
                normal = face.normal.copyOf(),
                area = face.area,
                planar = face.planar
            )
        }
    )
