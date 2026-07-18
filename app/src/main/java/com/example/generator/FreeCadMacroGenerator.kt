package com.example.generator

import com.example.model.*
import java.util.Locale

object FreeCadMacroGenerator {

    fun generate(sketches: List<CadSketch>, operations: List<CadOperation>, projectName: String): String {
        val sb = StringBuilder()
        val safeName = projectName.replace(" ", "_").replace("[^a-zA-Z0-9_]".toRegex(), "")

        sb.append("# -*- coding: utf-8 -*-\n")
        sb.append("# =========================================================================\n")
        sb.append("# FreeCAD Macro: $safeName.FCMacro\n")
        sb.append("# Generado por: SolidMacro CAD para Android\n")
        sb.append("# Fecha: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date())}\n")
        sb.append("# Descripcion: Secuencia de operaciones de modelado 3D (Extrusiones, Revolución,\n")
        sb.append("#              Cortes y Vaciados) optimizada para evitar errores topologicos.\n")
        sb.append("# =========================================================================\n\n")

        sb.append("import FreeCAD as App\n")
        sb.append("import Part\n")
        sb.append("import math\n\n")

        sb.append("# 1. Crear nuevo documento o usar el activo\n")
        sb.append("doc = App.activeDocument()\n")
        sb.append("if doc is None:\n")
        sb.append("    doc = App.newDocument(\"$safeName\")\n")
        sb.append("else:\n")
        sb.append("    # Guardar en variables para no perder referencia\n")
        sb.append("    print(\"Agregando operaciones al documento activo: \" + doc.Name)\n\n")

        sb.append("# Helper para crear vectores\n")
        sb.append("def V(x, y, z=0.0):\n")
        sb.append("    return App.Vector(x, y, z)\n\n")

        sb.append("# Almacen de solidos primarios y cortes\n")
        sb.append("solid_shapes = []\n")
        sb.append("cut_shapes = []\n\n")

        // Map sketches by ID for quick reference
        val sketchMap = sketches.associateBy { it.id }

        sb.append("# 2. Definición de Geometrías y Operaciones\n")
        operations.forEachIndexed { opIndex, op ->
            sb.append("# --- Operación [${opIndex + 1}]: ${op.name} (${op.type.name}) ---\n")
            val sketch = op.sketchId?.let { sketchMap[it] }

            if (sketch == null && op.type != OperationType.SHELL && op.type != OperationType.FILLET) {
                sb.append("# Error: No se encontró el croquis asociado para esta operación.\n\n")
                return@forEachIndexed
            }

            // Normal and center based on workplane
            val plane = sketch?.plane ?: WorkPlane.XY
            val (nx, ny, nz) = when (plane) {
                WorkPlane.XY -> Triple(0.0, 0.0, 1.0)
                WorkPlane.XZ -> Triple(0.0, 1.0, 0.0)
                WorkPlane.YZ -> Triple(1.0, 0.0, 0.0)
            }

            if (sketch != null) {
                sb.append("# Generar croquis: ${sketch.name} en Plano ${sketch.plane.name}\n")
                sb.append("wires_${op.id} = []\n")

                sketch.entities.forEachIndexed { entIdx, ent ->
                    when (ent) {
                        is SketchEntity.Line -> {
                            // Map 2D to 3D plane
                            val p1 = to3D(ent.x1, ent.y1, plane)
                            val p2 = to3D(ent.x2, ent.y2, plane)
                            sb.append("line_$entIdx = Part.makeLine(V(${p1.first}, ${p1.second}, ${p1.third}), V(${p2.first}, ${p2.second}, ${p2.third}))\n")
                            sb.append("wires_${op.id}.append(line_$entIdx)\n")
                        }
                        is SketchEntity.Circle -> {
                            val center = to3D(ent.cx, ent.cy, plane)
                            sb.append("circle_$entIdx = Part.makeCircle(${ent.radius}, V(${center.first}, ${center.second}, ${center.third}), V($nx, $ny, $nz))\n")
                            sb.append("wires_${op.id}.append(Part.Wire(circle_$entIdx))\n")
                        }
                        is SketchEntity.Rectangle -> {
                            // Calculate 4 corners in 2D
                            val xL = ent.cx - ent.width / 2f
                            val xR = ent.cx + ent.width / 2f
                            val yB = ent.cy - ent.height / 2f
                            val yT = ent.cy + ent.height / 2f

                            val c1 = to3D(xL, yB, plane)
                            val c2 = to3D(xR, yB, plane)
                            val c3 = to3D(xR, yT, plane)
                            val c4 = to3D(xL, yT, plane)

                            sb.append("rect_l1 = Part.makeLine(V(${c1.first}, ${c1.second}, ${c1.third}), V(${c2.first}, ${c2.second}, ${c2.third}))\n")
                            sb.append("rect_l2 = Part.makeLine(V(${c2.first}, ${c2.second}, ${c2.third}), V(${c3.first}, ${c3.second}, ${c3.third}))\n")
                            sb.append("rect_l3 = Part.makeLine(V(${c3.first}, ${c3.second}, ${c3.third}), V(${c4.first}, ${c4.second}, ${c4.third}))\n")
                            sb.append("rect_l4 = Part.makeLine(V(${c4.first}, ${c4.second}, ${c4.third}), V(${c1.first}, ${c1.second}, ${c1.third}))\n")
                            sb.append("wires_${op.id}.append(Part.Wire([rect_l1, rect_l2, rect_l3, rect_l4]))\n")
                        }
                    }
                }

                sb.append("# Combinar líneas sueltas en contornos cerrados (Wires)\n")
                sb.append("wires_combined_${op.id} = Part.Wire(Part.show(Part.Compound(wires_${op.id}))[0].Shape.Edges)\n")
                sb.append("face_${op.id} = Part.Face(wires_combined_${op.id})\n")
            }

            // Perform operation
            when (op.type) {
                OperationType.EXTRUDE_BOSS -> {
                    sb.append("# Extrusión - Añadir Material\n")
                    sb.append("extrusion_${op.id} = face_${op.id}.extrude(V(${nx * op.depth}, ${ny * op.depth}, ${nz * op.depth}))\n")
                    sb.append("solid_shapes.append(extrusion_${op.id})\n\n")
                }
                OperationType.EXTRUDE_CUT -> {
                    sb.append("# Extrusión - Cortar / Vaciar Material (Vaciado Integrado)\n")
                    sb.append("extrusion_cut_${op.id} = face_${op.id}.extrude(V(${nx * op.depth}, ${ny * op.depth}, ${nz * op.depth}))\n")
                    sb.append("cut_shapes.append(extrusion_cut_${op.id})\n\n")
                }
                OperationType.REVOLVE_BOSS -> {
                    sb.append("# Revolución - Añadir Material\n")
                    val (ax, ay, az) = when (op.axis) {
                        "X-Axis" -> Triple(1.0, 0.0, 0.0)
                        else -> Triple(0.0, 1.0, 0.0) // Y-Axis
                    }
                    sb.append("revolve_${op.id} = face_${op.id}.revolve(V(0,0,0), V($ax, $ay, $az), ${op.angle})\n")
                    sb.append("solid_shapes.append(revolve_${op.id})\n\n")
                }
                OperationType.REVOLVE_CUT -> {
                    sb.append("# Revolución - Cortar / Vaciar Material\n")
                    val (ax, ay, az) = when (op.axis) {
                        "X-Axis" -> Triple(1.0, 0.0, 0.0)
                        else -> Triple(0.0, 1.0, 0.0) // Y-Axis
                    }
                    sb.append("revolve_cut_${op.id} = face_${op.id}.revolve(V(0,0,0), V($ax, $ay, $az), ${op.angle})\n")
                    sb.append("cut_shapes.append(revolve_cut_${op.id})\n\n")
                }
                OperationType.FILLET -> {
                    sb.append("# Aplicar Redondeo a la última forma sólida generada\n")
                    sb.append("if len(solid_shapes) > 0:\n")
                    sb.append("    last_solid = solid_shapes[-1]\n")
                    sb.append("    try:\n")
                    sb.append("        # Redondea todos los bordes externos\n")
                    sb.append("        filleted = last_solid.makeFillet(${op.radius}, last_solid.Edges)\n")
                    sb.append("        solid_shapes[-1] = filleted\n")
                    sb.append("        print(\"Redondeo aplicado exitosamente\")\n")
                    sb.append("    except Exception as e:\n")
                    sb.append("        print(\"No se pudo aplicar redondeo en todos los bordes: \" + str(e))\n\n")
                }
                OperationType.SHELL -> {
                    sb.append("# Aplicar Vaciado (Shell) a la última forma sólida\n")
                    sb.append("if len(solid_shapes) > 0:\n")
                    sb.append("    last_solid = solid_shapes[-1]\n")
                    sb.append("    try:\n")
                    sb.append("        # En FreeCAD, makeThickness vacía sólidos con un grosor\n")
                    sb.append("        # Se vacía removiendo una de las caras (la primera cara por defecto)\n")
                    sb.append("        faces_to_remove = [last_solid.Faces[0]]\n")
                    sb.append("        shelled = last_solid.makeThickness(faces_to_remove, -${op.thickness}, 1e-3)\n")
                    sb.append("        solid_shapes[-1] = shelled\n")
                    sb.append("        print(\"Vaciado (Shell) aplicado exitosamente\")\n")
                    sb.append("    except Exception as e:\n")
                    sb.append("        print(\"Error al aplicar Shell: \" + str(e))\n\n")
                }
            }
        }

        sb.append("# 3. Fusionar sólidos añadidos y restar operaciones de corte\n")
        sb.append("if len(solid_shapes) > 0:\n")
        sb.append("    final_shape = solid_shapes[0]\n")
        sb.append("    for i in range(1, len(solid_shapes)):\n")
        sb.append("        final_shape = final_shape.fuse(solid_shapes[i])\n\n")
        
        sb.append("    # Aplicar todos los cortes de vaciado/pocketing\n")
        sb.append("    for cut in cut_shapes:\n")
        sb.append("        final_shape = final_shape.cut(cut)\n\n")

        sb.append("    # 4. Crear objeto visual en FreeCAD y recomputar\n")
        sb.append("    obj = doc.addObject(\"Part::Feature\", \"$safeName\")\n")
        sb.append("    obj.Shape = final_shape\n")
        sb.append("    obj.ViewObject.ShapeColor = (0.75, 0.85, 0.95)\n")
        sb.append("    obj.ViewObject.DisplayMode = \"Shaded\"\n")
        sb.append("    doc.recompute()\n")
        sb.append("    print(\"¡Modelo $safeName generado exitosamente!\")\n")
        sb.append("else:\n")
        sb.append("    print(\"Error: No se definió ninguna operación base para extruir o revolucionar.\")\n")

        return sb.toString()
    }

    private fun to3D(x: Float, y: Float, plane: WorkPlane): Triple<Float, Float, Float> {
        return when (plane) {
            WorkPlane.XY -> Triple(x, y, 0f)
            WorkPlane.XZ -> Triple(x, 0f, y)
            WorkPlane.YZ -> Triple(0f, x, y)
        }
    }
}
