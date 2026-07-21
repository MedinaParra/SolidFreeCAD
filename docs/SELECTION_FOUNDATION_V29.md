# SolidFreeCAD Android — base de selección v2.9

Este hito continúa desde `agent/solidfreecad-android-stabilization` sin modificar `main`.

## Implementado en este commit

- Estado inmutable de multiselección topológica con máximo de 64 entidades.
- Selección aditiva y eliminación mediante `toggle`.
- Entidad activa independiente del conjunto seleccionado.
- Métricas agregadas de área de caras, longitud de aristas y perímetro de bucles.
- Invalidación explícita del conjunto cuando se instala una nueva malla BRep confirmada.
- Clasificación determinista de bucles de caras planas en región exterior, región interior o cadena abierta.
- Proyección a una base 2D local de la cara.
- Clasificación por profundidad de anidamiento impar/par, sin depender únicamente del sentido de triangulación.
- Área firmada, perímetro y profundidad de anidamiento disponibles para diagnóstico.

## Límite de honestidad

Los identificadores siguen derivados de la triangulación confirmada. No son nombres topológicos persistentes de OpenCASCADE y no deben conservarse después de recomputar o sustituir la malla.

La clasificación exterior/interior solo se aplica a caras detectadas como planas. En superficies curvas se conservan los bucles geométricos, pero no se inventa una región 2D.

Este commit todavía no conecta la selección aditiva con todos los controles Compose ni habilita edición de subformas STEP/FCStd importadas. Esa conexión requiere un siguiente cambio de interfaz y, para edición importada real, handles BRep persistentes expuestos por `FreeCAD-Native`.

## Validación

Las pruebas incluyen:

- agregar y retirar entidades del conjunto;
- control de la selección activa;
- métricas agregadas;
- cara anular plana con un bucle exterior y uno interior;
- área neta de la región;
- perímetros y profundidad de anidamiento.
