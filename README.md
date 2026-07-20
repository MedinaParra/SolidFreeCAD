# SolidFreeCAD

Aplicación CAD para Android escrita en Kotlin y Jetpack Compose. Su objetivo es ofrecer un entorno móvil inspirado en SolidWorks para crear geometría paramétrica y exportar macros `.FCMacro` compatibles con FreeCAD.

## Requisitos de desarrollo

- Android Studio compatible con Android Gradle Plugin 9.1.1
- JDK 17
- Android SDK 36 y Build Tools 36.0.0
- Gradle 9.3.1 cuando se compile fuera de Android Studio

## Abrir y ejecutar

1. Clona el repositorio y abre la carpeta raíz en Android Studio.
2. Selecciona JDK 17 como **Gradle JDK**.
3. Instala Android SDK 36 si Android Studio lo solicita.
4. Sincroniza el proyecto con Gradle.
5. Ejecuta la variante `debug` en un dispositivo o emulador con Android 7.0 o superior.

El proyecto no necesita una clave de Gemini, `google-services.json` ni un keystore personalizado para compilar la variante debug.

## Compilación automática

El workflow **Android build** usa JDK 17, Gradle 9.3.1 y Android SDK 36 para ejecutar:

```text
gradle :app:assembleDebug :app:testDebugUnitTest
```

Cuando la compilación finaliza correctamente, GitHub Actions publica el archivo `app-debug.apk` como artefacto `SolidFreeCAD-debug`.

## Estado actual

El entorno incluye:

- interfaz CAD en Jetpack Compose;
- árbol de operaciones y administrador de propiedades;
- visor geométrico interactivo;
- generación y exportación de macros FreeCAD.

El visor actual es un renderizador geométrico implementado con Compose Canvas; no incorpora todavía el núcleo OpenCascade/FreeCAD dentro de Android.
