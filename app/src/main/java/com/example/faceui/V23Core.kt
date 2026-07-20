package com.example.faceui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.features.BasicCadFeature
import com.example.features.BasicCadFeatureFamily
import com.example.features.BasicCadHistory
import com.example.features.BasicCadMacroGenerator
import com.example.features.BasicCadOperation
import com.example.features.BasicCadProgram
import com.example.features.CadFaceFrame
import com.example.features.CadReferencePlane
import com.example.features.CadReferencePlaneKind
import com.example.features.CadSketch
import com.example.features.CadSketchProfileType
import com.example.features.CadVector3
import com.example.features.defaultParameters
import com.example.features.requiresClosedSketch
import com.example.nativecad.viewer.CameraPreset
import com.example.nativecad.viewer.NativeSceneMesh
import com.example.ui.theme.MyApplicationTheme
import com.medinaparra.freecadandroid.io.AndroidDocumentLoader
import com.medinaparra.freecadandroid.io.FreeCadArchiveReader
import com.medinaparra.freecadandroid.macro.SolidFreeCadMacroRuntime
import com.medinaparra.freecadandroid.model.SceneMesh
import com.medinaparra.freecadandroid.nativebridge.NativeFreeCadFileBridge
import com.medinaparra.freecadandroid.nativebridge.NativeStepBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
internal val V23Text = Color(0xFF0F172A)
internal val V23Secondary = Color(0xFF334155)
internal val V23Muted = Color(0xFF475569)
internal val V23Accent = Color(0xFF15557C)
internal val V23Panel = Color(0xFFF8FAFC)
internal val V23Header = Color(0xFFE4ECF2)
internal val V23Divider = Color(0xFF94A3B8)
internal val V23Selected = Color(0xFFCBE6F6)
internal val V23Viewport = Color(0xFFE7ECEF)
internal val V23Dimension = Color(0xFF1565C0)
internal const val V23AnimMs = 230
internal const val V23Preferences = "solidfreecad_workbench_v23"

internal enum class V23NodeKind { BODY, PLANE, SKETCH, FEATURE, IMPORTED }
internal enum class V23DrawerTab(val label: String) {
    PLANOS("Planos"),
    CROQUIS("Croquis"),
    OPERACIONES("Operaciones"),
    CORTES("Cortes"),
    ACABADOS("Acabados"),
    PATRONES("Patrones")
}

internal data class V23Selection(val kind: V23NodeKind, val id: Long? = null)

internal data class V23Document(
    val name: String,
    val format: String,
    val mesh: NativeSceneMesh,
    val summary: String,
    val program: BasicCadProgram?,
    val token: Long,
    val fit: Boolean
)

internal data class V23ParameterValidation(
    val values: Map<String, Double> = emptyMap(),
    val error: String? = null
)

class SolidFreeCadWorkbenchActivityV23 : ComponentActivity() {
    private var cadSurface: FaceDrivenCadSurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialUri = intent?.data
        setContent {
            MyApplicationTheme {
                V23Workbench(initialUri) { cadSurface = it }
            }
        }
    }

    override fun onPause() {
        cadSurface?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        cadSurface?.onResume()
    }
}
