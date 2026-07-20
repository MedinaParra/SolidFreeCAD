package com.example.faceui

import android.content.Context
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import com.example.features.BasicCadMacroGenerator
import com.example.features.BasicCadProgram
import com.example.features.CadSketchProfileType
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
import kotlin.math.abs

internal fun <T> v23Tween() = tween<T>(V23AnimMs, easing = FastOutSlowInEasing)

internal fun v23ValidateParameters(draft: Map<String, String>): V23ParameterValidation {
    val values = linkedMapOf<String, Double>()
    for ((key, text) in draft) {
        val value = text.toDoubleOrNull()
            ?: return V23ParameterValidation(error = "El parámetro $key no es válido")
        if (!value.isFinite()) return V23ParameterValidation(error = "$key debe ser finito")
        if (key == "count") {
            if (value < 2.0 || abs(value - value.toInt().toDouble()) > 1.0e-9) {
                return V23ParameterValidation(error = "count debe ser un entero mayor o igual que 2")
            }
        } else if (key !in setOf("dx", "dy", "dz", "angle", "offset") && value <= 0.0) {
            return V23ParameterValidation(error = "$key debe ser mayor que cero")
        }
        values[key] = value
    }
    return V23ParameterValidation(values = values)
}

internal fun v23ValidateSketch(
    type: CadSketchProfileType,
    draft: Map<String, String>
): V23ParameterValidation {
    val result = v23ValidateParameters(draft)
    if (result.error != null) return result
    val values = result.values
    return when (type) {
        CadSketchProfileType.SLOT -> {
            val length = values["length"] ?: 0.0
            val width = values["width"] ?: 0.0
            if (length < width) V23ParameterValidation(error = "La longitud de la ranura debe ser mayor o igual que su ancho") else result
        }
        CadSketchProfileType.POLYGON -> {
            val sides = values["sides"] ?: 0.0
            if (sides < 3.0 || abs(sides - sides.toInt().toDouble()) > 1.0e-9) {
                V23ParameterValidation(error = "El polígono requiere al menos 3 lados enteros")
            } else result
        }
        else -> result
    }
}

internal fun v23MapsEqual(left: Map<String, Double>, right: Map<String, Double>): Boolean {
    if (left.keys != right.keys) return false
    return left.all { (key, value) -> abs(value - (right[key] ?: return false)) <= 1.0e-9 }
}

internal suspend fun v23BuildProgram(
    context: Context,
    program: BasicCadProgram,
    fit: Boolean
): V23Document = withContext(Dispatchers.IO) {
    val scene = SolidFreeCadMacroRuntime.execute(
        context,
        BasicCadMacroGenerator.generate(program),
        0.18,
        0.24
    )
    V23Document(
        name = "${program.documentName}.FCStd",
        format = "Modelo paramétrico",
        mesh = scene.mesh.v23Native(),
        summary = buildString {
            appendLine("FreeCAD Base 1.1.1 / Runtime 0.11")
            appendLine(scene.pythonVersion)
            appendLine(scene.documentSummary)
            appendLine("${program.planes.size} planos · ${program.sketches.size} croquis · ${program.features.size} operaciones")
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
        "step", "stp" -> NativeStepBridge.importStep(file.absolutePath, name).let {
            V23Document(name, "STEP", it.mesh.v23Native(), it.summary, null, System.nanoTime(), true)
        }
        "fcstd" -> {
            val archive = FreeCadArchiveReader.extract(file, File(context.cacheDir, "solidfreecad-fcstd-v23"), name)
            NativeFreeCadFileBridge.importFcStdObjects(archive.objects, name, archive.summary).let {
                V23Document(name, "FCStd", it.mesh.v23Native(), it.summary, null, System.nanoTime(), true)
            }
        }
        "fcmacro", "py" -> SolidFreeCadMacroRuntime.executeFile(context, file.readBytes(), 0.18, 0.24).let {
            V23Document(
                name,
                "FCMacro",
                it.mesh.v23Native(),
                "Macro FreeCAD ejecutada\n${it.pythonVersion}\n${it.documentSummary}\n${it.output}",
                null,
                System.nanoTime(),
                true
            )
        }
        else -> error("Formato no compatible: .$extension. Use STEP, FCStd o FCMacro")
    }
}

internal fun v23Extension(context: Context, uri: Uri, file: File, name: String): String {
    val extension = AndroidDocumentLoader.extension(name)
    if (extension in setOf("step", "stp", "fcstd", "fcmacro", "py")) return extension
    val mime = context.contentResolver.getType(uri).orEmpty().lowercase(Locale.ROOT)
    if ("python" in mime || "x-fcmacro" in mime) return "fcmacro"
    val prefix = file.inputStream().buffered().use { input ->
        val bytes = ByteArray(4096)
        val count = input.read(bytes)
        if (count > 0) bytes.copyOf(count).toString(Charsets.UTF_8) else ""
    }
    return if ("import FreeCAD" in prefix || "import Part" in prefix || "App.newDocument" in prefix) {
        "fcmacro"
    } else extension
}

internal fun SceneMesh.v23Native() = NativeSceneMesh(
    vertices = vertices,
    indices = indices,
    minX = minX,
    minY = minY,
    minZ = minZ,
    maxX = maxX,
    maxY = maxY,
    maxZ = maxZ
)

internal fun v23Numeric(value: String) = value.filterIndexed { index, character ->
    character.isDigit() || character == '.' || character == ',' || (character == '-' && index == 0)
}.replace(',', '.')

internal fun v23Failure(error: Throwable) =
    generateSequence(error) { it.cause }.last().message ?: error.message ?: error::class.java.simpleName

internal fun v23Mm(value: Double) = "%.2f mm".format(Locale.US, value)
internal fun v23Number(value: Double) = "%.4f".format(Locale.US, value).trimEnd('0').trimEnd('.')
internal fun v23Vector(value: CadVector3) = "(%.2f, %.2f, %.2f)".format(Locale.US, value.x, value.y, value.z)
