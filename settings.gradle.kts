import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.ZipInputStream

pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

val payloadDirectory = file(".agent-v31")
if (payloadDirectory.isDirectory) {
  val chunks = payloadDirectory.listFiles()
    ?.filter { it.isFile && it.name.startsWith("chunk-") }
    ?.sortedBy { it.name }
    .orEmpty()
  check(chunks.isNotEmpty()) { "SolidFreeCAD v3.1 payload chunks are missing" }

  val encoded = buildString { chunks.forEach { append(it.readText()) } }
  val archiveBytes = Base64.getDecoder().decode(encoded)
  val archiveHash = MessageDigest.getInstance("SHA-256")
    .digest(archiveBytes)
    .joinToString("") { "%02x".format(it) }
  check(archiveHash == "7eea8ab0ac331b9ed379e704a74e07dea5e282b5ec3c7ce94a98e7af9ec0343d") {
    "SolidFreeCAD v3.1 payload hash mismatch: $archiveHash"
  }

  val root = rootDir.canonicalFile
  ZipInputStream(ByteArrayInputStream(archiveBytes)).use { zip ->
    var entry = zip.nextEntry
    while (entry != null) {
      val destination = root.resolve(entry.name).canonicalFile
      check(destination.path.startsWith(root.path + java.io.File.separator)) {
        "Unsafe payload path: ${entry.name}"
      }
      if (entry.isDirectory) destination.mkdirs() else {
        destination.parentFile?.mkdirs()
        destination.outputStream().use(zip::copyTo)
      }
      zip.closeEntry()
      entry = zip.nextEntry
    }
  }

  fun replaceOnce(path: String, old: String, replacement: String) {
    val target = root.resolve(path)
    val source = target.readText()
    check(old in source) { "Samsung isolation patch target missing in $path" }
    target.writeText(source.replaceFirst(old, replacement))
  }

  fun replaceCount(path: String, old: String, replacement: String, expected: Int) {
    val target = root.resolve(path)
    val source = target.readText()
    check(source.split(old).size - 1 == expected) {
      "Expected $expected occurrences in $path"
    }
    target.writeText(source.replace(old, replacement))
  }

  replaceOnce(
    "app/src/main/java/com/example/faceui/CadSketchTouchV31.kt",
    "fun modelDelta(dx: Float, dy: Float): Pair<Double, Double> = dx / scale to -dy / scale",
    "fun modelDelta(dx: Float, dy: Float): Pair<Double, Double> = (dx / scale).toDouble() to (-dy / scale).toDouble()",
  )
  replaceCount(
    "app/src/test/java/com/example/faceui/CadSketchTouchV31Test.kt",
    "CadSketch.empty(\"S\")",
    "CadSketch(id = 1L, label = \"S\", planeId = 1L)",
    3,
  )
  replaceOnce(
    "app/src/main/v27gen/SolidFreeCadWorkbenchActivityV27.part1",
    "} else rebuild(history.reset(BasicCadProgram()), true, \"Pieza paramétrica creada\")",
    "} else {\nstatus = \"Inicio seguro activo · el núcleo CAD se cargará solo al abrir o crear un modelo\"\n}",
  )
  replaceOnce(
    "app/src/main/v27gen/SolidFreeCadWorkbenchActivityV27.part1",
    "onNew = { rebuild(history.reset(BasicCadProgram()), true, \"Pieza paramétrica creada\") },",
    "onNew = { rebuild(history.reset(BasicCadProgram()), true, \"Cargando núcleo CAD para crear una pieza…\") },",
  )
  replaceOnce(
    "app/src/main/v27gen/SolidFreeCadWorkbenchActivityV27.part1",
    "override fun onCreate(savedInstanceState: Bundle?) {\nsuper.onCreate(savedInstanceState)\nenableEdgeToEdge()\nval initialUri = intent?.data\nsetContent { MyApplicationTheme { V27Workbench(initialUri) { cadSurface = it } } }\n}",
    "override fun onCreate(savedInstanceState: Bundle?) {\nrunCatching { filesDir.resolve(\"cadengine-stage.txt\").writeText(\"1_activity_onCreate\") }\nsuper.onCreate(savedInstanceState)\nrunCatching { filesDir.resolve(\"cadengine-stage.txt\").writeText(\"2_after_super_onCreate\") }\nenableEdgeToEdge()\nval initialUri = intent?.data\nrunCatching { filesDir.resolve(\"cadengine-stage.txt\").writeText(\"3_before_setContent\") }\nsetContent { MyApplicationTheme { V27Workbench(initialUri) { cadSurface = it } } }\nrunCatching { filesDir.resolve(\"cadengine-stage.txt\").writeText(\"4_after_setContent\") }\n}",
  )
  replaceOnce(
    "app/src/main/v27gen/SolidFreeCadWorkbenchActivityV27.part1",
    "factory = { androidContext -> FaceDrivenCadSurfaceViewV27(androidContext).also { surface=it; onSurfaceReady(it) } },",
    "factory = { androidContext ->\nrunCatching { androidContext.filesDir.resolve(\"cadengine-stage.txt\").writeText(\"5_before_glsurface\") }\nFaceDrivenCadSurfaceViewV27(androidContext).also {\nrunCatching { androidContext.filesDir.resolve(\"cadengine-stage.txt\").writeText(\"6_glsurface_created\") }\nsurface=it; onSurfaceReady(it)\n}\n},",
  )
  replaceOnce(
    "app/src/main/java/com/example/faceui/FaceDrivenCadSurfaceViewV27.kt",
    "init {\n        setEGLContextClientVersion(2)\n        preserveEGLContextOnPause = true\n        setRenderer(cadRenderer)\n        renderMode = RENDERMODE_WHEN_DIRTY\n    }",
    "init {\n        runCatching { context.filesDir.resolve(\"cadengine-stage.txt\").writeText(\"5a_glsurface_init\") }\n        setEGLContextClientVersion(2)\n        preserveEGLContextOnPause = true\n        setRenderer(cadRenderer)\n        renderMode = RENDERMODE_WHEN_DIRTY\n        runCatching { context.filesDir.resolve(\"cadengine-stage.txt\").writeText(\"5b_renderer_attached\") }\n    }",
  )

  val launcher = root.resolve("app/src/main/java/com/example/faceui/SafeLauncherActivity.kt")
  launcher.parentFile.mkdirs()
  launcher.writeText(
    """
package com.example.faceui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

class SafeLauncherActivity : Activity() {
    private lateinit var stageText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        refreshStage()
    }

    override fun onResume() {
        super.onResume()
        if (::stageText.isInitialized) refreshStage()
    }

    private fun buildUi(): ScrollView {
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(32), dp(24), dp(32))
            setBackgroundColor(Color.rgb(238, 243, 247))
        }
        column.addView(TextView(this).apply {
            text = "SolidFreeCAD 3.1.2"
            textSize = 26f
            setTextColor(Color.rgb(25, 35, 45))
            gravity = Gravity.CENTER
        }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(TextView(this).apply {
            text = "Lanzador seguro para Samsung A26 5G"
            textSize = 16f
            setTextColor(Color.rgb(70, 85, 100))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(22))
        })
        column.addView(TextView(this).apply {
            text = "La capa Android inició correctamente. La interfaz y el motor CAD se prueban por separado para evitar que una caída del motor cierre el lanzador."
            textSize = 15f
            setTextColor(Color.rgb(25, 35, 45))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.WHITE)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(16)
        })

        fun addAction(label: String, action: () -> Unit) {
            column.addView(Button(this).apply {
                text = label
                textSize = 15f
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply {
                bottomMargin = dp(12)
            })
        }

        addAction("1 · Probar interfaz Compose sin OpenGL ni JNI") {
            startActivity(Intent().setClassName(packageName, "com.example.faceui.SolidFreeCadUiPreviewActivity"))
        }
        addAction("2 · Probar motor CAD aislado") {
            stageFile().writeText("0_launcher_requested_cadengine")
            startActivity(Intent().setClassName(packageName, "com.example.faceui.SolidFreeCadWorkbenchActivityV27"))
        }
        addAction("Actualizar diagnóstico") { refreshStage() }
        addAction("Copiar diagnóstico") {
            val report = diagnosticReport()
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("SolidFreeCAD diagnóstico", report))
            stageText.text = report + "\n\nDiagnóstico copiado."
        }

        stageText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(25, 35, 45))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.WHITE)
        }
        column.addView(stageText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return ScrollView(this).apply { addView(column) }
    }

    private fun stageFile(): File = File(filesDir, "cadengine-stage.txt")

    private fun refreshStage() {
        stageText.text = diagnosticReport()
    }

    private fun diagnosticReport(): String {
        val stage = runCatching { stageFile().takeIf(File::isFile)?.readText() }.getOrNull()
            ?: "sin intento de motor"
        return "Estado Android: lanzador seguro activo\n" +
            "Última etapa CAD: " + stage + "\n" +
            "Fabricante: " + Build.MANUFACTURER + "\n" +
            "Modelo: " + Build.MODEL + "\n" +
            "Android: " + Build.VERSION.RELEASE + " · SDK " + Build.VERSION.SDK_INT + "\n" +
            "ABI: " + Build.SUPPORTED_ABIS.joinToString()
    }
}
    """.trimIndent() + "\n"
  )

  replaceOnce("app/build.gradle.kts", "versionCode = 31", "versionCode = 33")
  replaceOnce(
    "app/build.gradle.kts",
    "versionName = \"3.1.0-touch-workflows-a1\"",
    "versionName = \"3.1.2-samsung-isolated-launcher-a1\"",
  )

  val manifest = root.resolve("app/src/main/AndroidManifest.xml")
  var manifestSource = manifest.readText()
  if ("android:largeHeap=\"true\"" !in manifestSource) {
    manifestSource = manifestSource.replaceFirst(
      "android:allowBackup=\"true\"",
      "android:allowBackup=\"true\"\n        android:largeHeap=\"true\"",
    )
  }
  val originalLauncher = """            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
"""
  check(originalLauncher in manifestSource) { "Original launcher filter missing" }
  manifestSource = manifestSource.replaceFirst(originalLauncher, "")

  val previewActivity = """        <activity
            android:name="com.example.faceui.SolidFreeCadUiPreviewActivity"
"""
  val safeActivity = """        <activity
            android:name="com.example.faceui.SafeLauncherActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:screenOrientation="unspecified"
            android:theme="@style/Theme.MyApplication">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <activity
            android:name="com.example.faceui.SolidFreeCadUiPreviewActivity"
"""
  check(previewActivity in manifestSource) { "Preview activity declaration missing" }
  manifestSource = manifestSource.replaceFirst(previewActivity, safeActivity)

  val workbenchActivity = """        <activity
            android:name="com.example.faceui.SolidFreeCadWorkbenchActivityV27"
            android:exported="true"
"""
  val isolatedWorkbench = """        <activity
            android:name="com.example.faceui.SolidFreeCadWorkbenchActivityV27"
            android:exported="true"
            android:process=":cadengine"
"""
  check(workbenchActivity in manifestSource) { "Workbench declaration missing" }
  manifestSource = manifestSource.replaceFirst(workbenchActivity, isolatedWorkbench)
  manifest.writeText(manifestSource)
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "SolidMacro CAD"
include(":app")
