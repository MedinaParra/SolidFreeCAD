package com.example.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BasicCadHistoryTest {
    @Test
    fun commitUndoRedoPreservesPrograms() {
        val initial = BasicCadProgram()
        val second = initial.append(BasicCadOperation.SIMPLE_HOLE)
        val third = second.append(BasicCadOperation.CIRCULAR_PATTERN)

        val committed = BasicCadHistory(initial).commit(second).commit(third)
        assertTrue(committed.canUndo)
        assertFalse(committed.canRedo)
        assertEquals(third, committed.current)

        val undone = committed.undo()
        assertNotNull(undone)
        assertEquals(second, undone!!.current)
        assertTrue(undone.canRedo)

        val redone = undone.redo()
        assertNotNull(redone)
        assertEquals(third, redone!!.current)
        assertFalse(redone.canRedo)
    }

    @Test
    fun newCommitClearsRedoBranch() {
        val initial = BasicCadProgram()
        val second = initial.append(BasicCadOperation.SIMPLE_HOLE)
        val third = second.append(BasicCadOperation.MIRROR)
        val alternative = second.append(BasicCadOperation.LINEAR_PATTERN)

        val undone = BasicCadHistory(initial).commit(second).commit(third).undo()!!
        val branched = undone.commit(alternative)

        assertEquals(alternative, branched.current)
        assertFalse(branched.canRedo)
        assertNull(branched.redo())
    }

    @Test
    fun historyIsBounded() {
        var history = BasicCadHistory(BasicCadProgram(), maxDepth = 3)
        repeat(6) {
            history = history.commit(history.current.append(BasicCadOperation.SIMPLE_HOLE))
        }
        assertEquals(3, history.undoStack.size)
    }
}
