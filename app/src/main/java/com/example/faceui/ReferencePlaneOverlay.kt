package com.example.faceui

import com.example.features.CadVector3
import com.example.features.PlanarFaceReference
import com.example.features.ResolvedCadPlane

data class ReferencePlaneOverlay(
    val id: Long,
    val origin: CadVector3,
    val normal: CadVector3,
    val xAxis: CadVector3,
    val selected: Boolean,
    val size: Float
)

fun ResolvedCadPlane.toOverlay(selectedId: Long?, size: Float): ReferencePlaneOverlay =
    ReferencePlaneOverlay(
        id = id,
        origin = origin,
        normal = normal,
        xAxis = xAxis,
        selected = id == selectedId,
        size = size
    )

data class PlanarFacePick(
    val pointX: Float,
    val pointY: Float,
    val pointZ: Float,
    val normalX: Float,
    val normalY: Float,
    val normalZ: Float,
    val xAxisX: Float,
    val xAxisY: Float,
    val xAxisZ: Float,
    val triangleIndex: Int
) {
    fun toReference(label: String = "Cara plana ${triangleIndex + 1}") = PlanarFaceReference(
        label = label,
        point = CadVector3(pointX.toDouble(), pointY.toDouble(), pointZ.toDouble()),
        normal = CadVector3(normalX.toDouble(), normalY.toDouble(), normalZ.toDouble()),
        xAxis = CadVector3(xAxisX.toDouble(), xAxisY.toDouble(), xAxisZ.toDouble())
    )
}
