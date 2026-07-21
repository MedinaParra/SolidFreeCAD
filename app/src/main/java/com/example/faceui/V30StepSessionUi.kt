package com.example.faceui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwipeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medinaparra.freecadandroid.nativebridge.NativeStepBridge
import com.medinaparra.freecadandroid.nativebridge.NativeStepSessionSnapshot
import java.io.File

@Composable
internal fun V30StepEditBar(
    session: NativeStepSessionSnapshot,
    selectedFace: CadViewportFaceSelectionV27?,
    distanceText: String,
    previewActive: Boolean,
    enabled: Boolean,
    onDistance: (String) -> Unit,
    onPreview: () -> Unit,
    onCommit: () -> Unit,
    onRollback: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nativeFace = selectedFace?.nativeFaceId
    val canPull = enabled && nativeFace != null && selectedFace.planar && distanceText.toDoubleOrNull()?.let { it != 0.0 } == true
    Surface(modifier = modifier, color = V24Panel, contentColor = V24Text, shadowElevation = 9.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("EDICIÓN STEP DIRECTA", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(
                        if (nativeFace == null) "Seleccione una cara plana" else "Cara OCCT $nativeFace · revisión ${session.revision}",
                        color = V24Secondary,
                        fontSize = 10.sp
                    )
                }
                Text(if (previewActive) "PREVIEW" else "CONFIRMADO", color = if (previewActive) V24Accent else V24Secondary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = distanceText,
                    onValueChange = onDistance,
                    label = { Text("Pull mm") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onPreview, enabled = canPull) {
                    Icon(Icons.Default.SwipeUp, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Previsualizar")
                }
                Button(onClick = onCommit, enabled = enabled && previewActive) {
                    Icon(Icons.Default.Check, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Confirmar")
                }
                OutlinedButton(onClick = onRollback, enabled = enabled && previewActive) {
                    Icon(Icons.Default.Close, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Cancelar")
                }
                OutlinedButton(onClick = onSave, enabled = enabled && !previewActive) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Guardar copia")
                }
            }
            Text(
                "Distancia positiva añade material; negativa realiza corte. El original no se sobrescribe.",
                color = V24Secondary,
                fontSize = 10.sp
            )
        }
    }
}

internal suspend fun v30SaveStepCopy(
    context: Context,
    snapshot: NativeStepSessionSnapshot,
    destination: Uri
): String {
    val temp = File(context.cacheDir, "solidfreecad-step-export-${snapshot.handle}-${snapshot.revision}.step")
    return try {
        val summary = NativeStepBridge.saveCopy(snapshot, temp.absolutePath)
        context.contentResolver.openOutputStream(destination, "wt")?.use { output ->
            temp.inputStream().use { input -> input.copyTo(output) }
        } ?: error("No se pudo abrir el destino STEP")
        summary
    } finally {
        temp.delete()
    }
}
