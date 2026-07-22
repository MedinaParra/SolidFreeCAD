package com.example.faceui

/**
 * Immutable selection state for additive topology selection.
 *
 * Identity is intentionally based on the mesh-derived id. The set is invalidated whenever a new
 * committed BRep mesh is installed, because these ids are not persistent OCCT topology names yet.
 */
data class CadTopologySelectionSetV29(
    val selections: List<CadViewportSelectionV27> = emptyList(),
    val activeId: String? = selections.lastOrNull()?.id
) {
    init {
        require(selections.size <= MAX_SELECTIONS) { "La selección supera el límite móvil de $MAX_SELECTIONS elementos" }
        require(selections.map { it.id }.distinct().size == selections.size) { "La selección contiene identificadores repetidos" }
        require(activeId == null || selections.any { it.id == activeId }) { "La selección activa no pertenece al conjunto" }
    }

    val active: CadViewportSelectionV27?
        get() = activeId?.let { id -> selections.firstOrNull { it.id == id } }
            ?: selections.lastOrNull()

    val size: Int get() = selections.size
    val isEmpty: Boolean get() = selections.isEmpty()
    val isMultiple: Boolean get() = selections.size > 1

    val faceCount: Int get() = selections.count { it is CadViewportFaceSelectionV27 }
    val edgeCount: Int get() = selections.count { it is CadViewportEdgeSelectionV27 }
    val vertexCount: Int get() = selections.count { it is CadViewportVertexSelectionV27 }
    val loopCount: Int get() = selections.count { it is CadViewportLoopSelectionV27 }

    val approximateArea: Float
        get() = selections.filterIsInstance<CadViewportFaceSelectionV27>().sumOf { it.approximateArea.toDouble() }.toFloat()

    val totalEdgeLength: Float
        get() = selections.filterIsInstance<CadViewportEdgeSelectionV27>().sumOf { it.length.toDouble() }.toFloat()

    val totalLoopPerimeter: Float
        get() = selections.filterIsInstance<CadViewportLoopSelectionV27>().sumOf { it.perimeter.toDouble() }.toFloat()

    fun replace(selection: CadViewportSelectionV27?): CadTopologySelectionSetV29 =
        if (selection == null) empty() else CadTopologySelectionSetV29(listOf(selection), selection.id)

    fun toggle(selection: CadViewportSelectionV27): CadTopologySelectionSetV29 {
        val existing = selections.indexOfFirst { it.id == selection.id }
        if (existing >= 0) {
            val updated = selections.toMutableList().apply { removeAt(existing) }
            return CadTopologySelectionSetV29(updated, updated.lastOrNull()?.id)
        }
        require(selections.size < MAX_SELECTIONS) { "La selección alcanzó el máximo de $MAX_SELECTIONS elementos" }
        return CadTopologySelectionSetV29(selections + selection, selection.id)
    }

    fun activate(id: String): CadTopologySelectionSetV29 =
        if (selections.any { it.id == id }) copy(activeId = id) else this

    fun clear(): CadTopologySelectionSetV29 = empty()

    companion object {
        const val MAX_SELECTIONS = 64
        fun empty() = CadTopologySelectionSetV29(emptyList(), null)
    }
}
