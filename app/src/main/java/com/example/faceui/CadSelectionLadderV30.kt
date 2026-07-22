package com.example.faceui

import kotlin.math.hypot

data class CadSelectionLadderResultV30(
    val selection: CadViewportSelectionV27?,
    val index: Int,
    val count: Int,
    val cycled: Boolean
) {
    val label: String?
        get() = selection?.let { "${index + 1}/$count · ${it.ladderLabelV30()}" }
}

class CadSelectionLadderV30(
    private val repeatRadiusPx: Float = 28f,
    private val repeatWindowMs: Long = 900L
) {
    private var previousX = Float.NaN
    private var previousY = Float.NaN
    private var previousTime = Long.MIN_VALUE
    private var previousIds: List<String> = emptyList()
    private var previousIndex = -1

    fun choose(x: Float, y: Float, eventTimeMs: Long, candidates: List<CadViewportSelectionV27>): CadSelectionLadderResultV30 {
        if (candidates.isEmpty()) {
            reset()
            return CadSelectionLadderResultV30(null, 0, 0, false)
        }
        val unique = candidates.distinctBy { it.id }
        val ids = unique.map { it.id }
        val repeated = previousTime != Long.MIN_VALUE &&
            eventTimeMs - previousTime in 0..repeatWindowMs &&
            hypot((x - previousX).toDouble(), (y - previousY).toDouble()) <= repeatRadiusPx &&
            ids == previousIds
        val index = if (repeated) (previousIndex + 1) % unique.size else 0
        previousX = x
        previousY = y
        previousTime = eventTimeMs
        previousIds = ids
        previousIndex = index
        return CadSelectionLadderResultV30(unique[index], index, unique.size, repeated)
    }

    fun reset() {
        previousX = Float.NaN
        previousY = Float.NaN
        previousTime = Long.MIN_VALUE
        previousIds = emptyList()
        previousIndex = -1
    }
}

internal fun CadViewportSelectionV27.ladderLabelV30(): String = when (this) {
    is CadViewportFaceSelectionV27 -> if (planar) "Cara plana" else "Superficie curva"
    is CadViewportEdgeSelectionV27 -> "Arista"
    is CadViewportVertexSelectionV27 -> "Vértice"
    is CadViewportLoopSelectionV27 -> role.label
}
