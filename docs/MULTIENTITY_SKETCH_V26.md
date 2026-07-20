# SolidFreeCAD 2.6 - Croquis de múltiples entidades y restricciones básicas

Esta iteración avanza el contrato `SPACECLAIM_WORKFLOW_CONVERGENCE_CONTRACT.md` sin modificar las zonas permanentes de la interfaz.

## Rastros preservados

- `UI-001..007`: árbol izquierdo, visor central, propiedades derechas y bandeja flotante inferior.
- `INT-005`: animación GPU/VSYNC de la extrusión cilíndrica base.
- `SEL-001..002`: selección BRep genérica de v2.5.
- `PLN-003` y `PLN-005`: planos creados desde caras y visibles/seleccionables en el visor.
- recuperación, diagnóstico, modo guantes y rollback BRep de v2.4.
- apertura STEP, FCStd y FCMacro.

## Rastros avanzados

- `SK-005`: un croquis puede contener múltiples círculos, rectángulos, líneas, arcos y polígonos.
- `SK-006`: restricciones persistentes horizontal, vertical, coincidente, igual longitud, igual radio y fijo.
- `FLOW-BRACKET-001`: un mismo croquis puede contener las regiones y geometría auxiliar necesarias para una pieza de taller.
- `IND-REC-001`: la recuperación v2 conserva entidades y restricciones y mantiene lectura compatible de sesiones v1.

## Flujo de usuario

1. Crear o seleccionar un croquis desde el árbol o la bandeja inferior.
2. Abrir **Editar croquis**.
3. Añadir varias entidades dentro del mismo entorno gráfico.
4. Seleccionar una entidad en la lista, modificar sus cotas y aplicar.
5. Añadir restricciones rápidas.
6. Ver simultáneamente todas las entidades, la selección naranja y los ejes cartesianos.
7. Aceptar una sola vez para reconstruir el BRep, actualizar el árbol, Deshacer/Rehacer y el autoguardado.
8. Extruir o cortar: todas las entidades cerradas del croquis se convierten en regiones de la operación; líneas y arcos abiertos se conservan como geometría auxiliar.

## Restricciones implementadas

- Horizontal y vertical para líneas.
- Coincidencia entre extremos de líneas.
- Igual longitud entre líneas.
- Igual radio entre círculos, arcos y polígonos.
- Fijación de entidad.

El solucionador es determinista y limitado: realiza varias pasadas sobre las restricciones en orden estable. Está pensado como base móvil segura, no como equivalencia completa del solver de Sketcher.

## Recuperación industrial

El formato de recuperación pasa a versión 2:

- persiste todas las entidades y restricciones;
- conserva CRC, escritura atómica y respaldo anterior;
- acepta y migra sesiones versión 1 con restricciones vacías;
- una reconstrucción fallida no reemplaza el último estado confirmado.

## Pruebas de aceptación

1. Crear dos líneas, hacer la primera horizontal y el inicio de la segunda coincidente con el final de la primera.
2. Crear dos círculos y aplicar igual radio.
3. Eliminar una entidad referenciada y comprobar que se eliminan sus restricciones inválidas.
4. Crear dos rectángulos y un círculo en el mismo croquis; Extruir debe generar las tres regiones.
5. Añadir una línea abierta al mismo croquis; no debe convertirse en sólido ni impedir las regiones cerradas.
6. Cerrar y recuperar la aplicación; todas las entidades y restricciones deben permanecer.
7. Deshacer/Rehacer después de aceptar el croquis debe mantener árbol, parámetros y BRep sincronizados.
8. La selección BRep, los planos visibles y la flecha amarilla de versiones anteriores deben seguir funcionando.

## Límites declarados

- No hay todavía arrastre táctil directo de puntos o entidades dentro del croquis.
- No están implementadas restricciones tangente, perpendicular, paralela, simetría, punto sobre objeto ni cotas dimensionales como restricciones solver.
- Los arcos y líneas son geometría abierta; aún no se detectan cadenas cerradas formadas por varios segmentos.
- Las regiones cerradas independientes se fusionan para Saliente-Extruir y se fusionan como herramienta para Corte-Extruir.
- No existe todavía semántica de bucles interiores para transformar automáticamente un círculo interno en agujero dentro de una región exterior.
- El editor selecciona entidades desde la lista lateral; la selección directa sobre el lienzo queda para una iteración posterior.
- La validación física en los teléfonos objetivo sigue siendo obligatoria antes de declarar el hito apto para producción de taller.

## Siguiente hito

`v2.7`: selección de aristas, vértices y bucles; regiones compuestas por cadenas de líneas; semántica exterior/interior; Pull y Move contextuales sobre caras BRep arbitrarias.
