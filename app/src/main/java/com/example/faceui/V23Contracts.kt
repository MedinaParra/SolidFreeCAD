package com.example.faceui

import androidx.compose.ui.graphics.Color
import com.example.features.BasicCadProgram
import com.example.features.PlanarFaceReference
import com.example.nativecad.viewer.NativeSceneMesh

internal val V23Text = Color(0xFF0B1220)
internal val V23Secondary = Color(0xFF334155)
internal val V23Muted = Color(0xFF475569)
internal val V23Accent = Color(0xFF15557C)
internal val V23Panel = Color(0xFFF8FAFC)
internal val V23Header = Color(0xFFE4ECF2)
internal val V23Divider = Color(0xFF94A3B8)
internal val V23Selected = Color(0xFFCBE6F6)
internal val V23Viewport = Color(0xFFE7ECEF)
internal const val V23AnimMs = 230
internal const val V23Preferences = "solidfreecad_workbench_v23"

internal enum class V23SelectionType { DOCUMENT, FEATURE, PLANE, SKETCH }
internal enum class V23DockCategory(val label: String) {
    PLANES("Planos"),
    SKETCH("Croquis"),
    FEATURES("Operaciones"),
    CUTS("Cortes"),
    FINISH("Acabados"),
    PATTERNS("Patrones"),
    BODIES("Cuerpos")
}
internal enum class V23PlaneDialogMode { OFFSET, FACE_PARALLEL }

internal data class V23Document(
    val name: String,
    val format: String,
    val mesh: NativeSceneMesh,
    val summary: String,
    val program: BasicCadProgram?,
    val token: Long,
    val fit: Boolean
)

internal data class V23PlaneDialogState(
    val mode: V23PlaneDialogMode,
    val parentPlaneId: Long? = null,
    val face: PlanarFaceReference? = null
)

internal data class V23ParameterValidation(
    val values: Map<String, Double> = emptyMap(),
    val error: String? = null
)
