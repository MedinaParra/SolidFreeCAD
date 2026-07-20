package com.example.faceui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.features.BasicCadHistory
import com.example.features.BasicCadProgram
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class V23WorkbenchController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    var history by mutableStateOf(BasicCadHistory(BasicCadProgram()))
        private set
    var document by mutableStateOf<V23Document?>(null)
        private set
    var selectionType by mutableStateOf(V23SelectionType.FEATURE)
        private set
    var selectionId by mutableStateOf(1L)
        private set
    var loading by mutableStateOf(false)
        private set
    var status by mutableStateOf("Preparando FreeCAD…")
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var selectedFace by mutableStateOf(EditableCadFace.NONE)
    var surface: FaceDrivenCadSurfaceView? = null

    val program: BasicCadProgram?
        get() = document?.program

    fun select(type: V23SelectionType, id: Long) {
        selectionType = type
        selectionId = id
    }

    fun clearError() { error = null }
    fun reportError(message: String?) { if (!message.isNullOrBlank()) error = message }
    fun setStatus(message: String) { status = message }

    fun newDocument() {
        rebuild(
            candidate = history.reset(BasicCadProgram()),
            fit = true,
            message = "Pieza paramétrica creada",
            selectType = V23SelectionType.FEATURE,
            selectId = 1L
        )
    }

    fun open(uri: Uri) {
        if (loading) return
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        loading = true
        error = null
        status = "Abriendo documento…"
        selectedFace = EditableCadFace.NONE
        surface?.setSelectedFace(EditableCadFace.NONE)
        scope.launch {
            runCatching { v23LoadExternal(context, uri) }
                .onSuccess {
                    document = it
                    it.program?.let { loaded -> history = history.reset(loaded) }
                    selectionType = V23SelectionType.DOCUMENT
                    selectionId = 0L
                    status = "${it.format} cargado"
                }
                .onFailure {
                    error = v23Failure(it)
                    status = "Error al abrir el archivo"
                }
            loading = false
        }
    }

    fun commit(
        next: BasicCadProgram,
        fit: Boolean,
        message: String,
        selectType: V23SelectionType? = null,
        selectId: Long? = null
    ) {
        if (next == history.current) {
            status = "No hay cambios para aplicar"
            return
        }
        rebuild(history.commit(next), fit, message, selectType, selectId)
    }

    fun undo() {
        history.undo()?.let { rebuild(it, false, "Operación deshecha") }
    }

    fun redo() {
        history.redo()?.let { rebuild(it, false, "Operación rehecha") }
    }

    private fun rebuild(
        candidate: BasicCadHistory,
        fit: Boolean,
        message: String,
        selectType: V23SelectionType? = null,
        selectId: Long? = null
    ) {
        if (loading) return
        loading = true
        error = null
        selectedFace = EditableCadFace.NONE
        surface?.setSelectedFace(EditableCadFace.NONE)
        scope.launch {
            runCatching { v23BuildProgram(context, candidate.current, fit) }
                .onSuccess {
                    history = candidate
                    document = it
                    if (selectType != null && selectId != null) {
                        selectionType = selectType
                        selectionId = selectId
                    } else {
                        selectionType = V23SelectionType.FEATURE
                        selectionId = candidate.current.features.lastOrNull()?.id ?: 0L
                    }
                    status = message
                }
                .onFailure {
                    error = v23Failure(it)
                    status = "No se pudo reconstruir el modelo"
                    surface?.restoreCommittedPreview()
                }
            loading = false
        }
    }
}
