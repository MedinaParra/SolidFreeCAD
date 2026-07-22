package com.example.faceui

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.io.File
import java.lang.reflect.InvocationTargetException

class ProgressiveCadActivity : ComponentActivity() {
    private lateinit var statusText: TextView
    private var workbenchInstalled = false
    private var installingWorkbench = false

    override fun onCreate(savedInstanceState: Bundle?) {
        stage("P1_progressive_onCreate_enter")
        super.onCreate(savedInstanceState)
        stage("P2_progressive_after_super")
        showBootstrap()
        stage("P3_progressive_bootstrap_visible")

        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_AUTO_INSTALL, true)) {
            window.decorView.postDelayed({ installWorkbench() }, AUTO_INSTALL_DELAY_MS)
        }
    }

    override fun onResume() {
        super.onResume()
        if (workbenchInstalled) invokeLoaderLifecycle("resumeSurface")
    }

    override fun onPause() {
        if (workbenchInstalled) invokeLoaderLifecycle("pauseSurface")
        super.onPause()
    }

    override fun onDestroy() {
        if (workbenchInstalled) invokeLoaderLifecycle("releaseSurface")
        super.onDestroy()
    }

    private fun showBootstrap(error: Throwable? = null) {
        workbenchInstalled = false
        installingWorkbench = false
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(36), dp(24), dp(28))
            setBackgroundColor(Color.rgb(238, 243, 247))
        }
        column.addView(TextView(this).apply {
            text = "SolidFreeCAD 3.1.6"
            textSize = 28f
            setTextColor(Color.rgb(24, 34, 44))
            gravity = Gravity.CENTER
        }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(TextView(this).apply {
            text = if (error == null) "Preparando entorno CAD" else "Modo seguro del entorno CAD"
            textSize = 16f
            setTextColor(Color.rgb(74, 88, 102))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(20))
        })
        column.addView(TextView(this).apply {
            text = if (error == null) {
                "El árbol, el visor OpenGL y las herramientas STEP se cargarán sobre esta pantalla protegida."
            } else {
                "El entorno completo no pudo terminar de cargar. La aplicación permanece abierta para copiar el diagnóstico o volver a intentarlo."
            }
            textSize = 15f
            setTextColor(Color.rgb(28, 40, 52))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.WHITE)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(16)
        })

        column.addView(Button(this).apply {
            text = if (error == null) "Abrir entorno CAD" else "Reintentar carga CAD"
            textSize = 16f
            setOnClickListener { installWorkbench() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)).apply { bottomMargin = dp(12) })

        column.addView(Button(this).apply {
            text = "Copiar diagnóstico de carga"
            textSize = 15f
            setOnClickListener {
                val report = diagnosticReport()
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("SolidFreeCAD carga progresiva", report))
                statusText.text = report + "\n\nDiagnóstico copiado."
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { bottomMargin = dp(14) })

        statusText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(24, 34, 44))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.WHITE)
            text = diagnosticReport(error)
        }
        column.addView(statusText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(ScrollView(this).apply { addView(column) })
    }

    private fun installWorkbench() {
        if (installingWorkbench || workbenchInstalled || isFinishing) return
        installingWorkbench = true
        stage("P4_before_loader_class")
        statusText.text = "Cargando árbol, visor y herramientas…"
        runCatching {
            val loaderType = Class.forName("com.example.faceui.ProgressiveWorkbenchLoader", true, classLoader)
            stage("P5_loader_class_ready")
            val install = loaderType.getMethod("install", ComponentActivity::class.java)
            stage("P6_before_loader_install")
            install.invoke(null, this)
            workbenchInstalled = true
            installingWorkbench = false
            stage("P7_loader_install_returned")
            window.decorView.postDelayed({
                if (workbenchInstalled && !isFinishing) {
                    reportFile().delete()
                    stage("PRODUCT_READY")
                }
            }, PRODUCT_READY_DELAY_MS)
        }.onFailure { failure ->
            val root = if (failure is InvocationTargetException) failure.targetException ?: failure else failure
            reportFile().writeText(root.stackTraceToString())
            stage("P_FAIL_" + root.javaClass.simpleName)
            showBootstrap(root)
        }
    }

    private fun invokeLoaderLifecycle(methodName: String) {
        runCatching {
            val loaderType = Class.forName("com.example.faceui.ProgressiveWorkbenchLoader", false, classLoader)
            loaderType.getMethod(methodName).invoke(null)
        }.onFailure { failure ->
            reportFile().writeText("Lifecycle " + methodName + "\n" + failure.stackTraceToString())
        }
    }

    private fun stage(value: String) {
        runCatching { stageFile().writeText(value) }
    }

    private fun stageFile(): File = File(filesDir, "progressive-stage.txt")
    private fun reportFile(): File = File(filesDir, "progressive-report.txt")

    private fun diagnosticReport(error: Throwable? = null): String {
        val stage = runCatching { stageFile().readText() }.getOrDefault("sin etapa")
        val persisted = runCatching { reportFile().readText() }.getOrDefault("sin error registrado")
        return buildString {
            appendLine("Modo: carga progresiva 3.1.6")
            appendLine("Etapa: " + stage)
            appendLine("Archivo solicitado: " + (intent.data?.toString() ?: "ninguno"))
            appendLine("Error actual: " + (error?.let { it.javaClass.name + ": " + (it.message ?: "sin mensaje") } ?: "ninguno"))
            appendLine("Registro: " + persisted)
        }.trim()
    }

    companion object {
        const val EXTRA_AUTO_INSTALL = "solidfreecad.auto_install_workbench"
        private const val AUTO_INSTALL_DELAY_MS = 180L
        private const val PRODUCT_READY_DELAY_MS = 1_200L
    }
}
