# Gate de interfaz v2.9

Este gate valida la conexión del conjunto `CadTopologySelectionSetV29` con Compose, la superficie táctil y el renderizador OpenGL.

Debe comprobar:

- selección individual y múltiple de caras, aristas, vértices y bucles;
- segundo toque para retirar una entidad en modo aditivo;
- selección activa y selecciones secundarias simultáneas;
- colores diferenciados para región exterior, interior y cadena abierta;
- métricas acumuladas en Propiedades;
- invalidación al sustituir la malla BRep confirmada;
- conservación de la compilación universal ARM32/ARM64 y de todas las pruebas heredadas.

Este gate no afirma nombres topológicos OCCT persistentes ni edición BRep de subformas STEP/FCStd importadas.
