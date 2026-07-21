// Top-level build configuration for SolidFreeCAD.
// AGP 9.x provides built-in Kotlin support; only the Compose compiler plugin
// needs to be declared separately.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
}
