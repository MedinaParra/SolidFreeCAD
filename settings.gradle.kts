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

val safeStartPayload = file(".agent-v31")
if (safeStartPayload.isDirectory) {
  val chunks = safeStartPayload.listFiles()
    ?.filter { it.isFile && it.name.startsWith("chunk-") }
    ?.sortedBy { it.name }
    .orEmpty()
  check(chunks.isNotEmpty()) { "SolidFreeCAD v3.1 payload chunks are missing" }

  val encoded = buildString { chunks.forEach { append(it.readText()) } }
  val archiveBytes = Base64.getDecoder().decode(encoded)
  val digest = MessageDigest.getInstance("SHA-256")
    .digest(archiveBytes)
    .joinToString("") { "%02x".format(it) }
  check(digest == "7eea8ab0ac331b9ed379e704a74e07dea5e282b5ec3c7ce94a98e7af9ec0343d") {
    "SolidFreeCAD v3.1 payload hash mismatch: $digest"
  }

  val root = rootDir.canonicalFile
  ZipInputStream(ByteArrayInputStream(archiveBytes)).use { archive ->
    var entry = archive.nextEntry
    while (entry != null) {
      val destination = root.resolve(entry.name).canonicalFile
      check(destination.path.startsWith(root.path + java.io.File.separator)) {
        "Unsafe payload path: ${entry.name}"
      }
      if (entry.isDirectory) {
        destination.mkdirs()
      } else {
        destination.parentFile?.mkdirs()
        destination.outputStream().use { output -> archive.copyTo(output) }
      }
      archive.closeEntry()
      entry = archive.nextEntry
    }
  }

  fun patch(relativePath: String, old: String, replacement: String, requiredCount: Int = 1) {
    val target = root.resolve(relativePath)
    val source = target.readText()
    check(source.windowed(old.length, 1).count { it == old } >= requiredCount) {
      "Safe-start patch target missing in $relativePath"
    }
    target.writeText(source.replace(old, replacement))
  }

  patch(
    "app/src/main/java/com/example/faceui/CadSketchTouchV31.kt",
    "fun modelDelta(dx: Float, dy: Float): Pair<Double, Double> = dx / scale to -dy / scale",
    "fun modelDelta(dx: Float, dy: Float): Pair<Double, Double> = (dx / scale).toDouble() to (-dy / scale).toDouble()",
  )
  patch(
    "app/src/test/java/com/example/faceui/CadSketchTouchV31Test.kt",
    "CadSketch.empty(\"S\")",
    "CadSketch(id = 1L, label = \"S\", planeId = 1L)",
    requiredCount = 3,
  )
  patch(
    "app/src/main/v27gen/SolidFreeCadWorkbenchActivityV27.part1",
    "} else rebuild(history.reset(BasicCadProgram()), true, \"Pieza paramétrica creada\")",
    "} else {\nstatus = \"Inicio seguro activo · el núcleo CAD se cargará solo al abrir o crear un modelo\"\n}",
  )
  patch(
    "app/src/main/v27gen/SolidFreeCadWorkbenchActivityV27.part1",
    "onNew = { rebuild(history.reset(BasicCadProgram()), true, \"Pieza paramétrica creada\") },",
    "onNew = { rebuild(history.reset(BasicCadProgram()), true, \"Cargando núcleo CAD para crear una pieza…\") },",
  )
  patch("app/build.gradle.kts", "versionCode = 31", "versionCode = 32")
  patch(
    "app/build.gradle.kts",
    "versionName = \"3.1.0-touch-workflows-a1\"",
    "versionName = \"3.1.1-samsung-safe-start-a1\"",
  )
  val manifest = root.resolve("app/src/main/AndroidManifest.xml")
  if ("android:largeHeap=\"true\"" !in manifest.readText()) {
    patch(
      "app/src/main/AndroidManifest.xml",
      "android:allowBackup=\"true\"",
      "android:allowBackup=\"true\"\n        android:largeHeap=\"true\"",
    )
  }
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
