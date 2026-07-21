plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

fun resolveSolidFreeCadCommit(): String {
  val environmentCommit = System.getenv("SOLIDFREECAD_SOURCE_COMMIT")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: System.getenv("GITHUB_SHA")?.trim()?.takeIf { it.isNotEmpty() }
  if (environmentCommit != null) return environmentCommit.take(40)

  return runCatching {
    ProcessBuilder("git", "rev-parse", "HEAD")
      .redirectErrorStream(true)
      .start()
      .inputStream
      .bufferedReader()
      .use { it.readText().trim() }
      .take(40)
      .ifBlank { "unknown" }
  }.getOrDefault("unknown")
}

val solidFreeCadCommit = resolveSolidFreeCadCommit()

android {
  namespace = "com.example"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.aistudio.solidmacro.fctech"
    minSdk = 24
    targetSdk = 36
    versionCode = 30
    versionName = "3.0.0-step-session-a1"

    ndk {
      abiFilters += listOf("armeabi-v7a", "arm64-v8a")
    }

    buildConfigField("String", "SOLIDFREECAD_COMMIT", "\"$solidFreeCadCommit\"")
    buildConfigField("String", "FREECAD_RUNTIME_VERSION", "\"0.12.0-step-session\"")
    buildConfigField("String", "FREECAD_SOURCE_VERSION", "\"1.1.1\"")
    buildConfigField("String", "FREECAD_RUNTIME_COMMIT", "\"eb0e10de251f9e4ed6c94dc18a0e620e767946da\"")
    buildConfigField("String", "FREECAD_NATIVE_COMMIT", "\"eb0e10de251f9e4ed6c94dc18a0e620e767946da\"")
    buildConfigField("String", "OCCT_VERSION", "\"7.9.2\"")
    buildConfigField("String", "CPYTHON_VERSION", "\"3.14.6\"")
    buildConfigField("String", "CAD_BACKEND_NAME", "\"FreeCAD-Native / OpenCASCADE\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  packaging {
    jniLibs.useLegacyPackaging = true
  }

  testOptions {
    unitTests.isIncludeAndroidResources = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.robolectric)

  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)

  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}

val generatedV27Directory = file("build/generated/source/v27")
val generateV27Sources = tasks.register("generateV27Sources") {
  val first = file("src/main/v27gen/SolidFreeCadWorkbenchActivityV27.part1")
  val second = file("src/main/v27gen/SolidFreeCadWorkbenchActivityV27.part2")
  val output = file("$generatedV27Directory/com/example/faceui/SolidFreeCadWorkbenchActivityV27.kt")
  inputs.files(first, second)
  outputs.file(output)
  doLast {
    output.parentFile.mkdirs()
    output.writeText(first.readText() + second.readText())
  }
}
android.sourceSets.getByName("main").java.srcDir(generatedV27Directory)
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(generateV27Sources) }
