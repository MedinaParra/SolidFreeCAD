package com.example.faceui

/** Lightweight renderer contract for reference planes and picked surface frames. */
data class ReferencePlaneRender(
    val id: Long,
    val originX: Float,
    val originY: Float,
    val originZ: Float,
    val xAxisX: Float,
    val xAxisY: Float,
    val xAxisZ: Float,
    val yAxisX: Float,
    val yAxisY: Float,
    val yAxisZ: Float,
    val halfSize: Float,
    val selected: Boolean,
    val visible: Boolean
)

data class PickedFaceFrame(
    val reference: String,
    val pointX: Float,
    val pointY: Float,
    val pointZ: Float,
    val normalX: Float,
    val normalY: Float,
    val normalZ: Float,
    val xAxisX: Float,
    val xAxisY: Float,
    val xAxisZ: Float,
    val yAxisX: Float,
    val yAxisY: Float,
    val yAxisZ: Float
)
