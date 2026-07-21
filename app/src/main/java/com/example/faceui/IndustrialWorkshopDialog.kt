package com.example.faceui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.medinaparra.freecadandroid.nativebridge.NativeBackendRegistry
import com.medinaparra.freecadandroid.runtime.BaseRuntimeDescriptor

@Composable
internal fun IndustrialWorkshopDialog(
    gloveMode: Boolean,
    autosaveStatus: String,
    hasRecovery: Boolean,
    onGloveMode: (Boolean) -> Unit,
    onExportDiagnostics: () -> Unit,
    onClearRecovery: () -> Unit,
    onClearDiagnostics: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.HealthAndSafety, null) },
        title = { Text("Modo taller", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("SolidFreeCAD ${BuildConfig.VERSION_NAME}")
                Text("Commit app: ${BuildConfig.SOLIDFREECAD_COMMIT.take(12)}")
                Text("FreeCAD ${BuildConfig.FREECAD_SOURCE_VERSION} · Runtime ${BuildConfig.FREECAD_RUNTIME_VERSION}")
                Text("Commit motor: ${BuildConfig.FREECAD_NATIVE_COMMIT.take(12)}")
                Text("OCCT ${BuildConfig.OCCT_VERSION} · CPython ${BuildConfig.CPYTHON_VERSION}")
                Text("ABI: ${BaseRuntimeDescriptor.selectAbi(Build.SUPPORTED_ABIS.toList()) ?: "no compatible"}")
                Text("Backend: ${NativeBackendRegistry.activeBackend}")
                Text("Licencias: FreeCAD LGPL 2.1+, OCCT LGPL 2.1 + excepción, CPython PSF v2")
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Modo guantes", fontWeight = FontWeight.Bold)
                        Text("Aumenta las zonas táctiles principales.")
                    }
                    Switch(checked = gloveMode, onCheckedChange = onGloveMode)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Restore, null)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Recuperación automática", fontWeight = FontWeight.Bold)
                        Text(autosaveStatus)
                    }
                }
                Button(onClick = onExportDiagnostics, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Description, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Exportar diagnóstico")
                }
                OutlinedButton(onClick = onClearRecovery, enabled = hasRecovery, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.DeleteSweep, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Borrar sesión recuperable")
                }
                TextButton(onClick = onClearDiagnostics, modifier = Modifier.fillMaxWidth()) {
                    Text("Limpiar registro técnico")
                }
                Spacer(Modifier.height(2.dp))
                Text("Los registros permanecen solo en el teléfono hasta que se exportan manualmente.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}
