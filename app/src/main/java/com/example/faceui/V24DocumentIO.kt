package com.example.faceui

import android.content.Intent
import android.content.Context
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.features.*
import com.example.industrial.IndustrialCadDiagnostics
import com.example.industrial.IndustrialCadRecoveryStore
import com.example.industrial.IndustrialWorkshopPreferences
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


internal suspend fun v24BuildProgram(context: android.content.Context, program: BasicCadProgram, fit: Boolean): V24Document = withContext(Dispatchers.IO) {
    val scene = SolidFreeCadMacroRuntime.execute(context, BasicCadMacroGenerator.generate(program), 0.18, 0.24)
    V24Document("${program.documentName}.FCStd", "Modelo paramétrico", scene.mesh.v24Native(), "FreeCAD Base 1.1.1 / Runtime 0.11\n${scene.pythonVersion}\n${scene.documentSummary}", program, System.nanoTime(), fit)
}
internal suspend fun v24LoadExternal(context: android.content.Context, uri: Uri): V24Document = withContext(Dispatchers.IO) {
    val fallback = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.contains('.') } ?: "document.FCStd"
    val (file, name) = AndroidDocumentLoader.stage(context, uri, "solidfreecad-workbench-v24", fallback)
    val extension = v24Extension(context, uri, file, name)
    when (extension) {
        "step", "stp" -> NativeStepBridge.importStep(file.absolutePath, name).let { V24Document(name, "STEP", it.mesh.v24Native(), it.summary, null, System.nanoTime(), true) }
        "fcstd" -> { val archive = FreeCadArchiveReader.extract(file, File(context.cacheDir, "solidfreecad-fcstd-v24"), name); NativeFreeCadFileBridge.importFcStdObjects(archive.objects, name, archive.summary).let { V24Document(name, "FCStd", it.mesh.v24Native(), it.summary, null, System.nanoTime(), true) } }
        "fcmacro", "py" -> SolidFreeCadMacroRuntime.executeFile(context, file.readBytes(), 0.18, 0.24).let { V24Document(name, "FCMacro", it.mesh.v24Native(), "Macro FreeCAD ejecutada\n${it.pythonVersion}\n${it.documentSummary}\n${it.output}", null, System.nanoTime(), true) }
        else -> error("Formato no compatible: .$extension. Use STEP, FCStd o FCMacro")
    }
}
internal fun v24Extension(context: android.content.Context, uri: Uri, file: File, name: String): String {
    val ext = AndroidDocumentLoader.extension(name); if (ext in setOf("step", "stp", "fcstd", "fcmacro", "py")) return ext
    val mime = context.contentResolver.getType(uri).orEmpty().lowercase(Locale.ROOT); if ("python" in mime || "x-fcmacro" in mime) return "fcmacro"
    val prefix = file.inputStream().buffered().use { val b = ByteArray(4096); val n = it.read(b); if (n > 0) b.copyOf(n).toString(Charsets.UTF_8) else "" }
    return if ("import FreeCAD" in prefix || "import Part" in prefix || "App.newDocument" in prefix) "fcmacro" else ext
}
internal fun SceneMesh.v24Native() = NativeSceneMesh(vertices, indices, minX, minY, minZ, maxX, maxY, maxZ)
internal fun v24Mm(value: Double) = "%.2f mm".format(Locale.US, value)
internal fun v24Number(value: Double) = "%.4f".format(Locale.US, value).trimEnd('0').trimEnd('.')
internal fun v24Vec(value: CadVector3) = "(%.2f, %.2f, %.2f)".format(Locale.US, value.x, value.y, value.z)
