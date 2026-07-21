package com.example.features

/**
 * Immutable bounded undo/redo history for a SolidFreeCAD parametric program.
 *
 * A candidate state is only installed by the UI after the native BRep rebuild
 * succeeds, so a failed OpenCASCADE operation never corrupts the visible history.
 */
data class BasicCadHistory(
    val current: BasicCadProgram,
    val undoStack: List<BasicCadProgram> = emptyList(),
    val redoStack: List<BasicCadProgram> = emptyList(),
    val maxDepth: Int = 40
) {
    init {
        require(maxDepth in 1..200) { "History depth must be between 1 and 200" }
        require(undoStack.size <= maxDepth) { "Undo stack exceeds configured history depth" }
        require(redoStack.size <= maxDepth) { "Redo stack exceeds configured history depth" }
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun commit(next: BasicCadProgram): BasicCadHistory {
        if (next == current) return this
        return copy(
            current = next,
            undoStack = (undoStack + current).takeLast(maxDepth),
            redoStack = emptyList()
        )
    }

    fun undo(): BasicCadHistory? {
        val previous = undoStack.lastOrNull() ?: return null
        return copy(
            current = previous,
            undoStack = undoStack.dropLast(1),
            redoStack = (redoStack + current).takeLast(maxDepth)
        )
    }

    fun redo(): BasicCadHistory? {
        val next = redoStack.lastOrNull() ?: return null
        return copy(
            current = next,
            undoStack = (undoStack + current).takeLast(maxDepth),
            redoStack = redoStack.dropLast(1)
        )
    }

    fun reset(program: BasicCadProgram): BasicCadHistory = copy(
        current = program,
        undoStack = emptyList(),
        redoStack = emptyList()
    )
}
