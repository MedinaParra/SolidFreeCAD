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
    check(old in source) { "Progressive bootstrap patch target missing in $path" }
    target.writeText(source.replaceFirst(old, replacement))
  }

  fun replaceCount(path: String, old: String, replacement: String, expected: Int) {
    val target = root.resolve(path)
    val source = target.readText()
    check(source.split(old).size - 1 == expected) { "Expected $expected occurrences in $path" }
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
    "@Composable\nprivate fun V27Workbench",
    "@Composable\ninternal fun V27Workbench",
  )
  replaceOnce(
    "app/src/main/v27gen/SolidFreeCadWorkbenchActivityV27.part1",
    "} else rebuild(history.reset(BasicCadProgram()), true, \"Pieza paramétrica creada\")",
    "} else {\nstatus = \"Entorno CAD listo · use Nuevo o Abrir para cargar un modelo\"\n}",
  )
  replaceOnce(
    "app/src/main/v27gen/SolidFreeCadWorkbenchActivityV27.part1",
    "onNew = { rebuild(history.reset(BasicCadProgram()), true, \"Pieza paramétrica creada\") },",
    "onNew = { rebuild(history.reset(BasicCadProgram()), true, \"Cargando núcleo CAD para crear una pieza…\") },",
  )
  replaceOnce(
    "app/src/main/v27gen/SolidFreeCadWorkbenchActivityV27.part1",
    "factory = { androidContext -> FaceDrivenCadSurfaceViewV27(androidContext).also { surface=it; onSurfaceReady(it) } },",
    "factory = { androidContext ->\nrunCatching { androidContext.filesDir.resolve(\"progressive-stage.txt\").writeText(\"L3_before_cad_surface\") }\nFaceDrivenCadSurfaceViewV27(androidContext).also {\nrunCatching { androidContext.filesDir.resolve(\"progressive-stage.txt\").writeText(\"L4_cad_surface_ready\") }\nsurface=it; onSurfaceReady(it)\n}\n},",
  )
  replaceOnce(
    "app/src/main/java/com/example/faceui/FaceDrivenCadSurfaceViewV27.kt",
    "init {\n        setEGLContextClientVersion(2)\n        preserveEGLContextOnPause = true\n        setRenderer(cadRenderer)\n        renderMode = RENDERMODE_WHEN_DIRTY\n    }",
    "init {\n        runCatching { context.filesDir.resolve(\"progressive-stage.txt\").writeText(\"L3a_surface_init\") }\n        setEGLContextClientVersion(2)\n        preserveEGLContextOnPause = true\n        setRenderer(cadRenderer)\n        renderMode = RENDERMODE_WHEN_DIRTY\n        runCatching { context.filesDir.resolve(\"progressive-stage.txt\").writeText(\"L3b_renderer_attached\") }\n    }",
  )

  replaceOnce("app/build.gradle.kts", "versionCode = 31", "versionCode = 35")
  replaceOnce(
    "app/build.gradle.kts",
    "versionName = \"3.1.0-touch-workflows-a1\"",
    "versionName = \"3.1.4-progressive-bootstrap-a1\"",
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
  val productActivities = """        <activity
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
            android:name="com.example.faceui.ProgressiveCadActivity"
            android:exported="false"
            android:process=":cadengine"
            android:screenOrientation="fullSensor"
            android:theme="@style/Theme.MyApplication" />
        <activity
            android:name="com.example.faceui.CadEngineProbeActivity"
            android:exported="false"
            android:process=":cadengine"
            android:screenOrientation="unspecified"
            android:theme="@style/Theme.MyApplication" />
        <activity
            android:name="com.example.faceui.OpenGlProbeActivity"
            android:exported="false"
            android:process=":cadengine"
            android:screenOrientation="unspecified"
            android:theme="@style/Theme.MyApplication" />
        <activity
            android:name="com.example.faceui.NativeLoadProbeActivity"
            android:exported="false"
            android:process=":nativeprobe"
            android:screenOrientation="unspecified"
            android:theme="@style/Theme.MyApplication" />
        <activity
            android:name="com.example.faceui.SolidFreeCadUiPreviewActivity"
"""
  check(previewActivity in manifestSource) { "Preview activity declaration missing" }
  manifestSource = manifestSource.replaceFirst(previewActivity, productActivities)

  val workbenchActivity = """        <activity
            android:name="com.example.faceui.SolidFreeCadWorkbenchActivityV27"
            android:exported="true"
"""
  val isolatedWorkbench = """        <activity
            android:name="com.example.faceui.SolidFreeCadWorkbenchActivityV27"
            android:exported="false"
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
