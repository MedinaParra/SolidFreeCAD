package com.example.faceui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

class SafeLauncherActivity : Activity() {
    private lateinit var stageText: TextView
    private lateinit var diagnosticPanel: LinearLayout
    private lateinit var launchStatus: TextView
    private var launchDispatched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        refreshStage()

        val forceSafeMode = intent.getBooleanExtra(EXTRA_FORCE_SAFE_MODE, false)
        val previousStage = readText(progressiveStageFile())
        val shouldAutoOpen = savedInstanceState == null &&
            ProductStartupPolicy.shouldAutoOpen(previousStage, forceSafeMode)

        if (shouldAutoOpen) {
            launchStatus.text = if (intent.data != null) {
                "Preparando archivo en SolidFreeCAD…"
            } else {
                "Iniciando entorno CAD…"
            }
            window.decorView.postDelayed({
                if (!isFinishing) openProduct(intent.data, intent.type, autoInstall = true)
            }, AUTO_OPEN_DELAY_MS)
        } else if (!forceSafeMode && previousStage != null && !ProductStartupPolicy.isHealthy(previousStage)) {
            launchStatus.text = "Modo seguro activo: el último arranque CAD no terminó correctamente."
            diagnosticPanel.visibility = View.VISIBLE
        }
    }

    override fun onNewIntent(newIntent: Intent) {
        super.onNewIntent(newIntent)
        setIntent(newIntent)
        launchDispatched = false
        val uri = newIntent.data ?: return
        launchStatus.text = "Preparando archivo en SolidFreeCAD…"
        openProduct(uri, newIntent.type, autoInstall = true, sourceIntent = newIntent)
    }

    override fun onResume() {
        super.onResume()
        launchDispatched = false
        if (::stageText.isInitialized) refreshStage()
    }

    @Deprecated("Legacy Activity result is sufficient for this compatibility launcher")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != OPEN_DOCUMENT_REQUEST || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val permissionFlags = data.flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        runCatching { contentResolver.takePersistableUriPermission(uri, permissionFlags) }
        openProduct(uri, data.type, autoInstall = true, sourceIntent = data)
    }

    private fun buildUi(): ScrollView {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(34), dp(24), dp(30))
            setBackgroundColor(Color.rgb(238, 243, 247))
        }

        root.addView(TextView(this).apply {
            text = "SolidFreeCAD 3.1.6"
            textSize = 29f
            setTextColor(Color.rgb(25, 35, 45))
            gravity = Gravity.CENTER
        }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(TextView(this).apply {
            text = "CAD táctil con FreeCAD y OpenCASCADE"
            textSize = 16f
            setTextColor(Color.rgb(70, 85, 100))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(18))
        })

        launchStatus = TextView(this).apply {
            text = "Listo para modelar o abrir un archivo."
            textSize = 15f
            setTextColor(Color.rgb(25, 35, 45))
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(launchStatus, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(18)
        })

        fun action(parent: LinearLayout, label: String, click: () -> Unit) {
            parent.addView(Button(this).apply {
                text = label
                textSize = 15f
                setOnClickListener { runCatching(click).onFailure { writeFailure("launcher", it) } }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { bottomMargin = dp(10) })
        }

        action(root, "Abrir SolidFreeCAD") {
            openProduct(null, null, autoInstall = true)
        }
        action(root, "Abrir STEP, FCStd o macro") {
            val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf(
                        "application/step",
                        "application/stp",
                        "application/x-step",
                        "application/x-freecad-document",
                        "application/x-freecad-macro",
                        "application/octet-stream",
                        "text/x-python",
                        "text/plain",
                    ),
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            startActivityForResult(picker, OPEN_DOCUMENT_REQUEST)
        }
        action(root, "Abrir en modo seguro") {
            openProduct(null, null, autoInstall = false)
        }
        action(root, "Herramientas técnicas") {
            diagnosticPanel.visibility = if (diagnosticPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        diagnosticPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(8), 0, 0)
        }
        action(diagnosticPanel, "Vista previa de interfaz") {
            startActivity(Intent().setClassName(packageName, "com.example.faceui.SolidFreeCadUiPreviewActivity"))
        }
        action(diagnosticPanel, "Diagnóstico de GPU y proceso CAD") {
            stageFile().writeText("0_launcher_requested_probe")
            startActivity(Intent().setClassName(packageName, "com.example.faceui.CadEngineProbeActivity"))
        }
        action(diagnosticPanel, "Diagnóstico de librerías nativas") {
            nativeStageFile().writeText("N0_launcher_requested_nativeprobe")
            startActivity(Intent().setClassName(packageName, "com.example.faceui.NativeLoadProbeActivity"))
        }
        action(diagnosticPanel, "Copiar diagnóstico") {
            val report = diagnosticReport()
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("SolidFreeCAD diagnóstico", report))
            stageText.text = report + "\n\nDiagnóstico copiado."
        }

        stageText = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(25, 35, 45))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.WHITE)
        }
        diagnosticPanel.addView(stageText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(diagnosticPanel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return ScrollView(this).apply { addView(root) }
    }

    private fun openProduct(
        uri: Uri?,
        mimeType: String?,
        autoInstall: Boolean,
        sourceIntent: Intent? = null,
    ) {
        if (launchDispatched && autoInstall) return
        launchDispatched = autoInstall
        progressiveStageFile().writeText("0_launcher_requested_progressive")
        val target = Intent().setClassName(packageName, "com.example.faceui.ProgressiveCadActivity").apply {
            action = if (uri != null) Intent.ACTION_VIEW else Intent.ACTION_MAIN
            data = uri
            type = mimeType
            clipData = sourceIntent?.clipData ?: intent.clipData
            addFlags(
                (sourceIntent?.flags ?: intent.flags) and
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
            )
            putExtra(ProgressiveCadActivity.EXTRA_AUTO_INSTALL, autoInstall)
        }
        startActivity(target)
    }

    private fun stageFile(): File = File(filesDir, "cadengine-stage.txt")
    private fun reportFile(): File = File(filesDir, "cadengine-report.txt")
    private fun nativeStageFile(): File = File(filesDir, "nativeprobe-stage.txt")
    private fun nativeReportFile(): File = File(filesDir, "nativeprobe-report.txt")
    private fun progressiveStageFile(): File = File(filesDir, "progressive-stage.txt")
    private fun progressiveReportFile(): File = File(filesDir, "progressive-report.txt")

    private fun writeFailure(source: String, failure: Throwable) {
        val text = source + ": " + failure.javaClass.name + ": " + (failure.message ?: "sin mensaje")
        reportFile().writeText(text)
        launchStatus.text = text
        refreshStage()
    }

    private fun refreshStage() {
        stageText.text = diagnosticReport()
    }

    private fun readText(file: File): String? = runCatching { file.takeIf(File::isFile)?.readText() }.getOrNull()

    private fun diagnosticReport(): String {
        fun read(file: File, fallback: String): String = readText(file) ?: fallback
        return buildString {
            appendLine("Estado Android: lanzador SolidFreeCAD activo")
            appendLine("Carga progresiva: " + read(progressiveStageFile(), "sin intento"))
            appendLine("Informe progresivo: " + read(progressiveReportFile(), "sin informe"))
            appendLine("Última etapa CAD: " + read(stageFile(), "sin intento CAD"))
            appendLine("Informe CAD: " + read(reportFile(), "sin informe"))
            appendLine("Última etapa nativa: " + read(nativeStageFile(), "sin intento nativo"))
            appendLine("Informe nativo: " + read(nativeReportFile(), "sin informe"))
            appendLine("Fabricante: " + Build.MANUFACTURER)
            appendLine("Modelo: " + Build.MODEL)
            appendLine("Android: " + Build.VERSION.RELEASE + " · SDK " + Build.VERSION.SDK_INT)
            appendLine("ABI: " + Build.SUPPORTED_ABIS.joinToString())
        }.trim()
    }

    companion object {
        const val EXTRA_FORCE_SAFE_MODE = "solidfreecad.force_safe_mode"
        private const val OPEN_DOCUMENT_REQUEST = 3160
        private const val AUTO_OPEN_DELAY_MS = 280L
    }
}
