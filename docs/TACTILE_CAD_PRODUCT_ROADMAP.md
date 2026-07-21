# SolidFreeCAD Android — Roadmap de producto CAD táctil

Version del documento: 1.0  
Fecha de evaluación: 2026-07-21  
Rama evaluada: `agent/solidfreecad-v2.9-direct-selection`  
Commit funcional evaluado: `ee0ef70c0d44bacfb8f82ab9ee4982115db5e670`

## 1. Alcance

Este documento define el roadmap prioritario de SolidFreeCAD como modelador CAD táctil para teléfonos y tablets Android.

La referencia de interacción es la lógica de trabajo de SpaceClaim:

1. seleccionar geometría o una referencia;
2. elegir una herramienta contextual;
3. manipular directamente en el visor;
4. mostrar una previsualización reversible;
5. introducir opcionalmente una medida exacta;
6. confirmar una sola vez;
7. actualizar BRep, árbol, parámetros, historial y recuperación de forma sincronizada.

No se busca copiar la interfaz de escritorio de SpaceClaim, SolidWorks ni FreeCAD. La aplicación debe reemplazar hover obligatorio, botón central, clic derecho, Ctrl, iconos pequeños y paneles permanentes por una interacción apropiada para dedos, guantes, lápiz, mouse y tablet.

### Fuera de alcance prioritario

Quedan fuera de este roadmap de producto:

- cálculo FEM y CFD;
- solvers estructurales o fluidodinámicos;
- preparación avanzada para mallado;
- contactos destinados a simulación;
- vigas y midsurfaces orientadas a FEA;
- transferencia a ANSYS Workbench;
- herramientas complejas cuyo único objetivo sea la simulación.

Se mantienen como funciones CAD útiles los grupos, parámetros, selecciones nombradas, medición, reparación geométrica y edición de STEP/FCStd.

## 2. Principio de producto

El avance no se mide por la cantidad de comandos visibles. Una función se considera terminada solamente cuando:

- se puede alcanzar desde la interfaz permanente;
- permite seleccionar el objetivo con el dedo;
- entrega feedback visual claro;
- muestra previsualización fluida cuando modifica geometría;
- permite valor exacto cuando corresponde;
- puede cancelarse sin corromper el último BRep confirmado;
- actualiza árbol y propiedades;
- participa en Deshacer/Rehacer;
- conserva autoguardado y recuperación;
- puede validarse físicamente en teléfono y tablet;
- guarda y vuelve a abrir el resultado.

## 3. Flujo táctil universal

Todas las herramientas geométricas deben usar la misma máquina de estados:

```text
Seleccionar objetivo
→ seleccionar referencia opcional
→ manipular y previsualizar
→ introducir valor exacto opcional
→ confirmar o cancelar
```

Ejemplos:

```text
Pull:
Cara o región → dirección/eje/path/up-to → arrastrar → medida → confirmar

Move:
Cuerpo/cara → ancla/dirección/fulcro → arrastrar → medida → confirmar

Combine:
Cuerpo objetivo → cuerpos herramienta → regiones conservadas → confirmar

Split:
Cuerpo objetivo → cara/plano/cuerpo cortador → regiones conservadas → confirmar

Repair:
Problema detectado → previsualización → reparar uno/todos → confirmar
```

## 4. Gestos recomendados

| Gesto | Acción |
|---|---|
| Toque | Seleccionar una entidad |
| Segundo toque en el mismo punto | Recorrer entidades superpuestas mediante selection ladder |
| Toque en modo múltiple | Agregar o retirar una entidad |
| Mantener pulsado | Menú contextual y lupa de precisión |
| Arrastrar un manipulador | Modificar geometría |
| Arrastrar espacio vacío | Orbitar |
| Dos dedos | Pan y zoom |
| Pellizco | Zoom |
| Mantener y arrastrar en modo caja | Selección rectangular |
| Lápiz con hover | Preselección opcional |
| Doble toque en el cubo de vista | Ajustar vista al seleccionado |

Los dobles y triples toques no deben ser el único método para seleccionar loops o cuerpos, porque son menos confiables con el dedo. Deben existir acciones explícitas equivalentes.

## 5. Arquitectura adaptativa

### Teléfono

- Visor como región dominante.
- Árbol en panel lateral deslizable.
- Propiedades en bottom sheet.
- Una sola bandeja contextual abierta a la vez.
- Confirmar y Cancelar fijos y separados.
- Pull, Move, Fill y Combine siempre accesibles.
- Herramientas de menor frecuencia dentro de `Más`.
- Orientación horizontal para modelar y vertical para inspección/medición/propiedades.

### Tablet

- Árbol y propiedades persistentes cuando haya espacio.
- Paneles redimensionables.
- Visor con al menos la mitad del ancho disponible.
- Bandeja en una o dos filas, evitando desplazamientos horizontales largos.
- Soporte simultáneo para lápiz, mouse y teclado sin cambiar la lógica principal.

### Tamaños mínimos

- objetivo táctil normal: 48 x 48 dp;
- modo guantes: 56–64 dp;
- texto principal: 12–14 sp;
- texto secundario: 10–11 sp como mínimo;
- separación de 6–8 dp entre acciones incompatibles.

## 6. Roadmap por hitos

### Hito 1 — Base adaptativa de interfaz

Version propuesta: `2.9.1`

- Unificar las actividades versionadas en un Workbench modular.
- Crear `AdaptiveWorkbenchScaffold`.
- Breakpoints para teléfono, foldable y tablet.
- Escalar objetivos táctiles y tipografía.
- Paneles laterales y bottom sheets adaptativos.
- Estado persistente de modo, herramienta y fase.
- Confirmar/Cancelar común a todas las herramientas.
- Entrada numérica flotante con unidades.
- Conservar contexto al rotar, ocultar paneles o abrir propiedades.

Gate: ninguna operación principal depende únicamente de un formulario modal de escritorio.

### Hito 2 — Sesión BRep transaccional

Version propuesta: `3.0`

- Handle persistente del documento nativo.
- Handle por objeto BRep.
- Referencias reales a caras, aristas y vértices OCCT.
- Mapeo entre selección visual y subforma nativa.
- Sesiones `preview`, `commit` y `rollback`.
- Teselación incremental del objeto modificado.
- Guardar copia STEP y FCStd.
- Nunca modificar destructivamente el archivo original.

Gate: seleccionar una cara STEP, previsualizar una modificación, cancelar y recuperar exactamente el BRep anterior.

### Hito 3 — Selección táctil completa

Version propuesta: `3.1`

- Selección de cuerpo, superficie, croquis, plano, eje y componente.
- Selection ladder para entidades superpuestas.
- Caja contenida y caja cruzada.
- Lazo.
- Lupa para aristas y vértices.
- Power Selection por radio, diámetro, tamaño, coplanaridad, coaxialidad, tangencia, conectividad, cuerpo y tipo de rasgo.
- Filtros compatibles con dedo, guante, lápiz y mouse.

Gate: seleccionar todos los agujeros iguales de una pieza con tres acciones o menos.

### Hito 4 — Croquis realmente táctil

Version propuesta: `3.2`

- Dibujar directamente en el lienzo.
- Arrastrar puntos y entidades.
- Snaps a origen, punto medio, centro, tangente e intersección.
- Rejilla adaptable.
- Cotas gráficas editables.
- Teclado numérico contextual.
- Restricciones horizontal, vertical, coincidente, paralelo, perpendicular, tangente, igual, concéntrico, simétrico, punto sobre objeto y fijo.
- Trim, Extend, Offset, Project, Fillet, Chamfer, Split y Scale.
- Detección de cadenas cerradas.
- Bucles interiores convertidos en agujeros.
- Selección visual del perfil antes de extruir.

Gate: completar un soporte simple sin editar cada entidad desde una lista lateral.

### Hito 5 — Pull como herramienta principal

Version propuesta: `3.3`

- Extruir cara o perfil.
- Añadir, cortar o crear cuerpo.
- Ambas direcciones.
- Up To face/body.
- Through All.
- Offset de cara.
- Edición de radio y diámetro.
- Revolución.
- Sweep.
- Draft.
- Scale.
- Pull de arista para redondeo, chaflán o extrusión de borde.
- Previsualización fluida sin recomputar el BRep por cada píxel.

Gate: completar croquis, extrusión, agujeros, cortes, rounds y chamfers en un flujo táctil continuo.

### Hito 6 — Move y posicionamiento

Version propuesta: `3.4`

- Traslación y rotación.
- Manipulador 3D táctil.
- Reubicar ancla.
- Dirección mediante arista o eje.
- Movimiento por trayectoria.
- Movimiento radial.
- Fulcro.
- Up To.
- Copiar.
- Matriz rectangular y circular.
- Align, Orient y Tangent.

Gate: posicionar y copiar varios cuerpos mediante el manipulador sin editar coordenadas en un formulario.

### Hito 7 — Fill, Combine, Blend y Split

Version propuesta: `3.5`

- Fill de agujeros, salientes, depresiones, redondeos y chaflanes.
- Fill con bucles interiores.
- Guide curves.
- Blend entre caras, aristas y curvas.
- Combine Add/Subtract/Common.
- Conservar o eliminar cuerpos herramienta.
- Split Body, Split Face y Split Edge.
- Selección gráfica de regiones a conservar.
- Una operación equivale a una acción de Undo.

Gate: completar un flujo que use Split, Combine y Fill sin abandonar el visor.

### Hito 8 — Section, componentes y visualización

Version propuesta: `3.6`

- Section Mode con plano móvil.
- Edición de geometría interna.
- Clip volume.
- Vistas guardadas.
- Capas.
- Color por cuerpo o cara.
- Transparencia.
- Mostrar, ocultar y aislar.
- Componentes y componente activo.
- Abrir componente en pestaña.
- Drag-and-drop en el árbol.
- Mover a nuevo componente.
- Medición rápida persistente.

Gate: inspeccionar y editar una característica interna sin ocultar manualmente cada cuerpo.

### Hito 9 — Reparación BRep guiada

Version propuesta: `3.7`

- Diagnóstico después de importar.
- Stitch.
- Gaps.
- Missing Faces.
- Merge Faces.
- Split Edges.
- Extra Edges.
- Inexact Edges.
- Small Faces.
- Short Edges.
- Simplify.
- Remove Rounds.
- Remove Faces.
- Replace Face.
- Interference.
- Reparar uno por uno o todos.
- Navegación automática entre problemas.
- Previsualización antes de aplicar.

La reparación debe presentarse como asistente por problemas, no como una cinta llena de iconos pequeños.

Gate: importar geometría defectuosa, obtener un sólido cerrado y guardar el resultado.

### Hito 10 — STL y geometría facetada

Version propuesta: `3.8`

- Intersecciones.
- Agujeros.
- Non-manifold.
- Sharp features.
- Patch y cap.
- Selección de componentes conectados.
- Extracción de curvas.
- Conversión básica a BRep.
- Simplificación de caras.
- Detección asistida de planos, cilindros y conos.

Gate: reparar una malla de tamaño controlado sin agotar la memoria del dispositivo.

### Hito 11 — Grupos, parámetros y anotaciones

Version propuesta: `3.9`

- Grupos y selecciones nombradas.
- Driving dimensions.
- Renombrar parámetros.
- Tabla global de parámetros.
- Unidades y expresiones sencillas.
- Actualización segura del modelo.
- Cotas 3D, notas y vistas anotadas.
- Exportación de mediciones.

Gate: cambiar un parámetro nombrado y conservar selecciones y referencias posteriores.

### Hito 12 — Validación industrial

Version propuesta: `4.0`

- Pruebas físicas en teléfono y tablet.
- STEP pequeños, medianos y grandes.
- Sesiones prolongadas de órbita, selección y edición.
- Cambio de aplicación y recuperación de contexto.
- Memoria baja y presión térmica.
- Rotación, lápiz, guantes y foldables.
- Autosave y recuperación.
- Aislamiento del proceso nativo.
- Cancelación de operaciones largas.
- Guardar y reabrir en FreeCAD Desktop.

Gate: completar los flujos de aceptación en dispositivos objetivo sin pérdida de documento ni corrupción del BRep.

## 7. Orden de ejecución obligatorio

```text
Interfaz adaptativa
↓
BRep persistente y transacciones
↓
Selección precisa
↓
Croquis táctil
↓
Pull
↓
Move
↓
Fill / Combine / Split
↓
Section y componentes
↓
Repair
↓
STL
↓
Parámetros y anotaciones
↓
Validación industrial
```

No se deben añadir más comandos de catálogo antes de que los comandos prioritarios tengan un flujo completo y cancelable sobre geometría seleccionada.

## 8. Medición de avance

El porcentaje se calcula mediante ponderación de capacidad de producto, no por líneas de código ni número de botones.

| Hito | Peso | Estado actual | Contribución |
|---|---:|---:|---:|
| 1. Interfaz adaptativa | 10% | 40% | 4.00% |
| 2. BRep transaccional | 15% | 15% | 2.25% |
| 3. Selección táctil completa | 10% | 55% | 5.50% |
| 4. Croquis táctil | 12% | 35% | 4.20% |
| 5. Pull general | 12% | 30% | 3.60% |
| 6. Move general | 8% | 15% | 1.20% |
| 7. Fill/Combine/Blend/Split | 8% | 15% | 1.20% |
| 8. Section/componentes/visualización | 7% | 20% | 1.40% |
| 9. Reparación BRep | 7% | 5% | 0.35% |
| 10. STL/facetado | 4% | 10% | 0.40% |
| 11. Grupos/parámetros/anotaciones | 4% | 25% | 1.00% |
| 12. Validación industrial | 3% | 45% | 1.35% |
| **Total** | **100%** |  | **26.45%** |

Avance de producto redondeado: **26%**.

## 9. Evidencia del estado actual

### Implementado o avanzado

- Visor central, árbol, propiedades, barra superior y bandeja inferior.
- Renderizado OpenGL y navegación básica.
- Apertura STEP, FCStd y FCMacro con límites declarados.
- Catálogo paramétrico amplio de operaciones generado mediante FreeCAD.
- Croquis con varias entidades.
- Restricciones básicas y solucionador determinista limitado.
- Planos de referencia visibles y seleccionables.
- Selección BRep de caras, aristas, vértices y bucles.
- Multiselección aditiva de hasta 64 entidades.
- Clasificación planar de bucles exteriores e interiores.
- Métricas agregadas.
- Previsualización GPU/VSYNC para el cilindro base.
- Undo/Redo paramétrico.
- Autoguardado atómico, CRC, respaldo anterior y rollback del último estado válido.
- Diagnósticos amigables y logs exportables.
- Modo guantes.
- CI con pruebas y empaquetado universal ARM32/ARM64.

### Parcial o faltante

- La interfaz sigue bloqueada principalmente en paisaje.
- Existen varias actividades versionadas en lugar de un Workbench modular único.
- Algunos chips, pestañas e iconos son pequeños para dedos.
- No hay handles OCCT persistentes para subformas importadas.
- Los IDs topológicos siguen derivados de triangulación.
- No existe transacción nativa general de preview/commit/rollback.
- No existe edición arbitraria de caras STEP/FCStd con guardado transaccional.
- No hay selection ladder, caja cruzada, lazo ni power selection.
- El croquis se edita principalmente mediante listas y valores, no por arrastre directo completo.
- Faltan cadenas cerradas de segmentos y solver de restricciones completo.
- Pull directo está generalizado solo parcialmente; el cilindro base es la referencia más madura.
- Move, Fill, Split y Blend no tienen flujo táctil general sobre selección BRep.
- No existe Section Mode completo.
- Componentes, capas, vistas guardadas y aislamiento son parciales.
- No hay estación de reparación BRep ni STL completa.
- Faltan selecciones nombradas y driving dimensions generales.
- Falta validación física prolongada y aislamiento de crashes nativos.

## 10. Interpretación del 26%

El 26% no significa que la aplicación sea inutilizable. Significa que:

- la plataforma técnica y varios componentes fundamentales ya existen;
- la selección es uno de los subsistemas más avanzados;
- el catálogo de operaciones es más amplio que la madurez real de sus flujos;
- la mayor parte del trabajo restante está concentrada en integración nativa y experiencia táctil completa;
- una función generada por macro no se cuenta como terminada hasta que pueda aplicarse, previsualizarse, cancelarse y guardarse desde la selección táctil.

## 11. Próxima meta cuantitativa

Objetivo inmediato: llegar de **26% a 35%** completando:

1. interfaz adaptativa y tamaños táctiles;
2. Workbench modular único;
3. contrato de sesión BRep persistente;
4. selection ladder y selección de cuerpo;
5. primer Pull general sobre cara STEP con preview/cancel/commit.

No se debe declarar 35% hasta que ese primer flujo STEP sea físicamente probado y guardable.
