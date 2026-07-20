package com.example.faceui

import android.content.Context
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.JoinFull
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.features.BasicCadFeatureFamily
import com.example.features.BasicCadMacroGenerator
import com.example.features.BasicCadOperation
import com.example.features.BasicCadProgram
import com.example.features.CadVector3
import com.example.nativecad.viewer.NativeSceneMesh
import com.medinaparra.freecadandroid.io.AndroidDocumentLoader
import com.medinaparra.freecadandroid.io.FreeCadArchiveReader
import com.medinaparra.freecadandroid.macro.SolidFreeCadMacroRuntime
import com.medinaparra.freecadandroid.model.SceneMesh
import com.medinaparra.freecadandroid.nativebridge.NativeFreeCadFileBridge
import com.medinaparra.freecadandroid.nativebridge.NativeStepBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
internal fun V23ErrorCard(message: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.widthIn(max = 470.dp), color = Color(0xFFFFEBEE), contentColor = Color(0xFF5F1414), shape = RoundedCornerShape(8.dp), shadowElevation = 6.dp) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, null, tint = Color(0xFFC62828))
            Spacer(Modifier.width(7.dp))
            Text(message, Modifier.weight(1f), color = Color(0xFF5F1414), fontSize = 9.sp, fontWeight = FontWeight.Medium)
            IconButton(onClick = onClose, modifier = Modifier.size(42.dp)) { Icon(Icons.Default.Cancel, "Cerrar error", tint = Color(0xFF7F1D1D)) }
        }
    }
}

internal fun v23OperationsFor(category: V23DockCategory): List<BasicCadOperation> = when (category) {
    V23DockCategory.FEATURES -> listOf(BasicCadOperation.BOSS_EXTRUDE, BasicCadOperation.BOSS_REVOLVE, BasicCadOperation.BOSS_SWEEP, BasicCadOperation.BOSS_LOFT, BasicCadOperation.RIB)
    V23DockCategory.CUTS -> listOf(BasicCadOperation.CUT_EXTRUDE, BasicCadOperation.CUT_REVOLVE, BasicCadOperation.CUT_SWEEP, BasicCadOperation.CUT_LOFT, BasicCadOperation.SIMPLE_HOLE, BasicCadOperation.COUNTERBORE_HOLE, BasicCadOperation.COUNTERSINK_HOLE)
    V23DockCategory.FINISH -> listOf(BasicCadOperation.FILLET, BasicCadOperation.CHAMFER, BasicCadOperation.SHELL, BasicCadOperation.DRAFT)
    V23DockCategory.PATTERNS -> listOf(BasicCadOperation.LINEAR_PATTERN, BasicCadOperation.CIRCULAR_PATTERN, BasicCadOperation.MIRROR)
    V23DockCategory.BODIES -> listOf(BasicCadOperation.MOVE_COPY_BODY, BasicCadOperation.COMBINE_ADD, BasicCadOperation.COMBINE_SUBTRACT, BasicCadOperation.COMBINE_COMMON)
    else -> emptyList()
}

internal fun v23OperationIcon(operation: BasicCadOperation): ImageVector = when (operation.family) {
    BasicCadFeatureFamily.SKETCH_BASED -> Icons.Default.AddBox
    BasicCadFeatureFamily.REMOVE_MATERIAL -> Icons.Default.RemoveCircleOutline
    BasicCadFeatureFamily.DRESS_UP -> Icons.Default.AutoFixHigh
    BasicCadFeatureFamily.TRANSFORM -> Icons.Default.ContentCopy
    BasicCadFeatureFamily.BOOLEAN -> Icons.Default.JoinFull
}

internal fun <T> v23Tween() = tween<T>(V23AnimMs, easing = FastOutSlowInEasing)

internal fun v23ValidateParameters(draft: Map<String, String>): V23ParameterValidation {
    val parsed = linkedMapOf<String, Double>()
    for ((key, text) in draft) {
        val value = text.toDoubleOrNull() ?: return V23ParameterValidation(error = "${v23ParameterLabel(key)} debe contener un número válido")
        if (!value.isFinite()) return V23ParameterValidation(error = "${v23ParameterLabel(key)} debe ser finito")
        when (key) {
            "dx", "dy", "dz", "offset" -> Unit
            "count" -> if (value < 2.0 || value % 1.0 != 0.0) return V23ParameterValidation(error = "Cantidad debe ser un entero mayor o igual que 2")
            else -> if (value <= 0.0) return V23ParameterValidation(error = "${v23ParameterLabel(key)} debe ser mayor que cero")
        }
        parsed[key] = value
    }
    return V23ParameterValidation(parsed)
}

internal fun v23MapsEqual(left: Map<String, Double>, right: Map<String, Double>): Boolean = left.keys == right.keys && left.all { (key, value) -> kotlin.math.abs(value - (right[key] ?: Double.NaN)) < 1e-9 }

internal suspend fun v23BuildProgram(context: Context, program: BasicCadProgram, fit: Boolean): V23Document = withContext(Dispatchers.IO) {
    val scene = SolidFreeCadMacroRuntime.execute(context, BasicCadMacroGenerator.generate(program), 0.18, 0.24)
    V23Document(
        name = "${program.documentName}.FCStd",
        format = "Modelo paramétrico",
        mesh = scene.mesh.v23Native(),
        summary = buildString {
            appendLine("FreeCAD Base 1.1.1 / Runtime 0.11")
            appendLine(scene.pythonVersion)
            appendLine(scene.documentSummary)
            appendLine("${program.planes.size} planos · ${program.sketches.size} croquis · ${program.features.size} operaciones")
            if (scene.output.isNotBlank()) append(scene.output)
        }.trimEnd(),
        program = program,
        token = System.nanoTime(),
        fit = fit
    )
}

internal suspend fun v23LoadExternal(context: Context, uri: Uri): V23Document = withContext(Dispatchers.IO) {
    val fallback = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.contains('.') } ?: "document.FCStd"
    val (file, name) = AndroidDocumentLoader.stage(context, uri, "solidfreecad-workbench-v23", fallback)
    val extension = v23Extension(context, uri, file, name)
    when (extension) {
        "step", "stp" -> NativeStepBridge.importStep(file.absolutePath, name).let { V23Document(name, "STEP", it.mesh.v23Native(), it.summary, null, System.nanoTime(), true) }
        "fcstd" -> {
            val archive = FreeCadArchiveReader.extract(file, File(context.cacheDir, "solidfreecad-fcstd-v23"), name)
            NativeFreeCadFileBridge.importFcStdObjects(archive.objects, name, archive.summary).let { V23Document(name, "FCStd", it.mesh.v23Native(), it.summary, null, System.nanoTime(), true) }
        }
        "fcmacro", "py" -> SolidFreeCadMacroRuntime.executeFile(context, file.readBytes(), 0.18, 0.24).let { V23Document(name, "FCMacro", it.mesh.v23Native(), "Macro FreeCAD ejecutada\n${it.pythonVersion}\n${it.documentSummary}\n${it.output}", null, System.nanoTime(), true) }
        else -> error("Formato no compatible: .$extension. Use STEP, FCStd o FCMacro")
    }
}

internal fun v23Extension(context: Context, uri: Uri, file: File, name: String): String {
    val ext = AndroidDocumentLoader.extension(name)
    if (ext in setOf("step", "stp", "fcstd", "fcmacro", "py")) return ext
    val mime = context.contentResolver.getType(uri).orEmpty().lowercase(Locale.ROOT)
    if ("python" in mime || "x-fcmacro" in mime) return "fcmacro"
    val prefix = file.inputStream().buffered().use {
        val bytes = ByteArray(4096)
        val count = it.read(bytes)
        if (count > 0) bytes.copyOf(count).toString(Charsets.UTF_8) else ""
    }
    return if ("import FreeCAD" in prefix || "import Part" in prefix || "App.newDocument" in prefix) "fcmacro" else ext
}

internal fun SceneMesh.v23Native() = NativeSceneMesh(vertices = vertices, indices = indices, minX = minX, minY = minY, minZ = minZ, maxX = maxX, maxY = maxY, maxZ = maxZ)
internal fun v23Numeric(value: String) = value.filterIndexed { index, char -> char.isDigit() || char == '.' || char == ',' || (char == '-' && index == 0) }.replace(',', '.')
internal fun v23Failure(error: Throwable) = generateSequence(error) { it.cause }.last().message ?: error.message ?: error::class.java.simpleName
internal fun v23Mm(value: Double) = "%.2f mm".format(Locale.US, value)
internal fun v23Number(value: Double) = "%.4f".format(Locale.US, value).trimEnd('0').trimEnd('.')
internal fun v23Vector(value: CadVector3) = "(${v23Number(value.x)}, ${v23Number(value.y)}, ${v23Number(value.z)})"
internal fun v23ParameterLabel(key: String): String = when (key) {
    "diameter" -> "Diámetro"
    "depth" -> "Profundidad"
    "length" -> "Longitud"
    "width" -> "Ancho"
    "height" -> "Alto"
    "radius", "radius1", "radius2", "majorRadius", "minorRadius" -> "Radio"
    "thickness" -> "Espesor"
    "distance" -> "Distancia"
    "spacing" -> "Separación"
    "count" -> "Cantidad"
    "dx" -> "Desplazamiento X"
    "dy" -> "Desplazamiento Y"
    "dz" -> "Desplazamiento Z"
    else -> key
}
