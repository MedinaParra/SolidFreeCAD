package com.example.features

/** Builds a FreeCAD-compatible macro from the semantic feature and reference-plane model. */
object BasicCadMacroGenerator {
    fun generate(program: BasicCadProgram): String = buildString {
        appendLine("import FreeCAD as App")
        appendLine("import Part")
        appendLine("import math")
        appendLine()
        appendLine("doc = App.newDocument(${pythonString(program.documentName)})")
        appendLine(HELPERS)
        appendLine("body = None")

        val active = program.features.filterNot { it.suppressed }
        if (active.isEmpty()) {
            appendLine("body = Part.makeBox(20.0, 20.0, 20.0)")
        } else {
            active.forEachIndexed { index, feature ->
                val plane = program.planeSet.plane(feature.planeId)
                val sketch = feature.sketchId?.let { id -> program.sketches.firstOrNull { it.id == id } }
                appendLine("# ${feature.label}: ${feature.operation.description}")
                appendFeature(feature, plane, sketch, index)
                appendLine()
            }
        }

        appendLine("if body is None:")
        appendLine("    body = Part.makeBox(20.0, 20.0, 20.0)")
        appendLine("result = Part.show(body, 'Resultado')")
        appendLine("result.Label = ${pythonString(active.lastOrNull()?.label ?: "Resultado")}")
        appendLine("doc.recompute()")
        appendLine("print('SolidFreeCAD: ${active.size} operaciones reconstruidas')")
    }

    private fun StringBuilder.appendFeature(
        feature: BasicCadFeature,
        plane: CadReferencePlane,
        sketch: CadSketch?,
        index: Int
    ) {
        val p = feature.parameters
        fun n(key: String, fallback: Double): String = (p[key] ?: fallback).pythonNumber()
        val suffix = index + 1
        val placement = planePlacementArguments(plane)
        when (feature.operation) {
            BasicCadOperation.BOSS_EXTRUDE -> {
                appendSketchExtrusion("feature_$suffix", sketch, n("depth", 40.0), p)
                appendLine("feature_$suffix = _place(feature_$suffix, $placement)")
                appendLine("body = feature_$suffix if body is None else body.fuse(feature_$suffix)")
            }
            BasicCadOperation.CUT_EXTRUDE -> {
                appendSketchExtrusion("tool_$suffix", sketch, n("depth", 50.0), p)
                appendLine("tool_$suffix = _place(tool_$suffix, $placement)")
                appendLine("body = _require_body(body).cut(tool_$suffix)")
            }
            BasicCadOperation.BOSS_REVOLVE -> {
                appendLine("feature_$suffix = Part.makeTorus(${n("majorRadius", 24.0)}, ${n("minorRadius", 4.0)})")
                appendLine("feature_$suffix = _place(feature_$suffix, $placement)")
                appendLine("body = feature_$suffix if body is None else body.fuse(feature_$suffix)")
            }
            BasicCadOperation.CUT_REVOLVE -> {
                appendLine("tool_$suffix = Part.makeTorus(${n("majorRadius", 15.0)}, ${n("minorRadius", 2.2)})")
                appendLine("tool_$suffix = _place(tool_$suffix, $placement)")
                appendLine("body = _require_body(body).cut(tool_$suffix)")
            }
            BasicCadOperation.BOSS_SWEEP -> {
                appendLine("path_$suffix = [App.Vector(-${n("length", 38.0)}/2.0,0,0), App.Vector(0,0,${n("rise", 18.0)}), App.Vector(${n("length", 38.0)}/2.0,0,0)]")
                appendLine("feature_$suffix = _pipe_polyline(path_$suffix, ${n("radius", 2.5)})")
                appendLine("feature_$suffix = _place(feature_$suffix, $placement)")
                appendLine("body = feature_$suffix if body is None else body.fuse(feature_$suffix)")
            }
            BasicCadOperation.CUT_SWEEP -> {
                appendLine("path_$suffix = [App.Vector(-${n("length", 45.0)}/2.0,0,0), App.Vector(0,0,${n("rise", 12.0)}), App.Vector(${n("length", 45.0)}/2.0,0,0)]")
                appendLine("tool_$suffix = _pipe_polyline(path_$suffix, ${n("radius", 1.8)})")
                appendLine("tool_$suffix = _place(tool_$suffix, $placement)")
                appendLine("body = _require_body(body).cut(tool_$suffix)")
            }
            BasicCadOperation.BOSS_LOFT -> {
                appendLine("feature_$suffix = Part.makeCone(${n("radius1", 12.0)}, ${n("radius2", 6.0)}, ${n("height", 20.0)})")
                appendLine("feature_$suffix = _place(feature_$suffix, $placement)")
                appendLine("body = feature_$suffix if body is None else body.fuse(feature_$suffix)")
            }
            BasicCadOperation.CUT_LOFT -> {
                appendLine("tool_$suffix = Part.makeCone(${n("radius1", 7.0)}, ${n("radius2", 3.0)}, ${n("height", 50.0)})")
                appendLine("tool_$suffix = _place(tool_$suffix, $placement)")
                appendLine("body = _require_body(body).cut(tool_$suffix)")
            }
            BasicCadOperation.FILLET -> {
                appendLine("feature_$suffix = _rounded_box(${n("length", 42.0)}, ${n("width", 30.0)}, ${n("height", 18.0)}, ${n("radius", 3.0)})")
                appendLine("body = _place(feature_$suffix, $placement)")
            }
            BasicCadOperation.CHAMFER -> {
                appendLine("feature_$suffix = _chamfered_prism(${n("length", 42.0)}, ${n("width", 30.0)}, ${n("height", 18.0)}, ${n("distance", 4.0)})")
                appendLine("body = _place(feature_$suffix, $placement)")
            }
            BasicCadOperation.SHELL -> {
                appendLine("outer_$suffix = Part.makeCylinder(${n("radius", 18.0)}, ${n("height", 32.0)})")
                appendLine("inner_$suffix = Part.makeCylinder(${n("radius", 18.0)}-${n("thickness", 2.5)}, ${n("height", 32.0)}, App.Vector(0,0,${n("thickness", 2.5)}))")
                appendLine("feature_$suffix = outer_$suffix.cut(inner_$suffix)")
                appendLine("body = _place(feature_$suffix, $placement)")
            }
            BasicCadOperation.DRAFT -> {
                appendLine("feature_$suffix = Part.makeCone(${n("radius1", 18.0)}, ${n("radius2", 14.0)}, ${n("height", 30.0)})")
                appendLine("body = _place(feature_$suffix, $placement)")
            }
            BasicCadOperation.RIB -> {
                appendLine("points_$suffix = [App.Vector(-${n("length", 28.0)}/2.0,0,0), App.Vector(${n("length", 28.0)}/2.0,0,0), App.Vector(0,0,${n("height", 18.0)}), App.Vector(-${n("length", 28.0)}/2.0,0,0)]")
                appendLine("feature_$suffix = Part.Face(Part.makePolygon(points_$suffix)).extrude(App.Vector(0,${n("thickness", 3.0)},0))")
                appendLine("feature_$suffix.translate(App.Vector(0,-${n("thickness", 3.0)}/2.0,0))")
                appendLine("feature_$suffix = _place(feature_$suffix, $placement)")
                appendLine("body = feature_$suffix if body is None else body.fuse(feature_$suffix)")
            }
            BasicCadOperation.SIMPLE_HOLE -> {
                appendLine("tool_$suffix = Part.makeCylinder(${n("diameter", 8.0)}/2.0, ${n("depth", 80.0)}, App.Vector(0,0,-${n("depth", 80.0)}/2.0))")
                appendLine("tool_$suffix = _place(tool_$suffix, $placement)")
                appendLine("body = _require_body(body).cut(tool_$suffix)")
            }
            BasicCadOperation.COUNTERBORE_HOLE -> {
                appendLine("through_$suffix = Part.makeCylinder(${n("diameter", 7.0)}/2.0, 80.0, App.Vector(0,0,-40.0))")
                appendLine("seat_$suffix = Part.makeCylinder(${n("counterboreDiameter", 13.0)}/2.0, ${n("counterboreDepth", 5.0)}, App.Vector(0,0,-${n("counterboreDepth", 5.0)}/2.0))")
                appendLine("tool_$suffix = _place(through_$suffix.fuse(seat_$suffix), $placement)")
                appendLine("body = _require_body(body).cut(tool_$suffix)")
            }
            BasicCadOperation.COUNTERSINK_HOLE -> {
                appendLine("through_$suffix = Part.makeCylinder(${n("diameter", 7.0)}/2.0, 80.0, App.Vector(0,0,-40.0))")
                appendLine("sink_$suffix = Part.makeCone(${n("sinkDiameter", 14.0)}/2.0, ${n("diameter", 7.0)}/2.0, ${n("sinkDepth", 4.0)}, App.Vector(0,0,-${n("sinkDepth", 4.0)}/2.0))")
                appendLine("tool_$suffix = _place(through_$suffix.fuse(sink_$suffix), $placement)")
                appendLine("body = _require_body(body).cut(tool_$suffix)")
            }
            BasicCadOperation.LINEAR_PATTERN -> {
                appendLine("instances_$suffix = []")
                appendLine("for i in range(int(${n("count", 4.0)})):")
                appendLine("    x = (i-(int(${n("count", 4.0)})-1)/2.0)*${n("spacing", 14.0)}")
                appendLine("    instances_$suffix.append(Part.makeCylinder(${n("diameter", 5.0)}/2.0,8.0,App.Vector(x,0,0)))")
                appendLine("feature_$suffix = _place(_fuse_all(instances_$suffix), $placement)")
                appendLine("body = _require_body(body).fuse(feature_$suffix)")
            }
            BasicCadOperation.CIRCULAR_PATTERN -> {
                appendLine("instances_$suffix = []")
                appendLine("for i in range(int(${n("count", 6.0)})):")
                appendLine("    a = 2.0*math.pi*i/int(${n("count", 6.0)})")
                appendLine("    instances_$suffix.append(Part.makeCylinder(${n("diameter", 4.0)}/2.0,8.0,App.Vector(${n("radius", 14.0)}*math.cos(a),${n("radius", 14.0)}*math.sin(a),0)))")
                appendLine("feature_$suffix = _place(_fuse_all(instances_$suffix), $placement)")
                appendLine("body = _require_body(body).fuse(feature_$suffix)")
            }
            BasicCadOperation.MIRROR -> {
                appendLine("feature_$suffix = Part.makeCylinder(${n("diameter", 6.0)}/2.0,10.0,App.Vector(-${n("offset", 18.0)},0,0)).fuse(Part.makeCylinder(${n("diameter", 6.0)}/2.0,10.0,App.Vector(${n("offset", 18.0)},0,0)))")
                appendLine("feature_$suffix = _place(feature_$suffix, $placement)")
                appendLine("body = _require_body(body).fuse(feature_$suffix)")
            }
            BasicCadOperation.MOVE_COPY_BODY -> {
                appendLine("copy_$suffix = _require_body(body).copy()")
                appendLine("copy_$suffix.translate(App.Vector(${n("dx", 45.0)},${n("dy", 0.0)},${n("dz", 0.0)}))")
                appendLine("body = body.fuse(copy_$suffix)")
            }
            BasicCadOperation.COMBINE_ADD -> {
                appendLine("tool_$suffix = Part.makeBox(${n("size", 14.0)},${n("size", 14.0)},${n("size", 14.0)},App.Vector(-${n("size", 14.0)}/2.0,-${n("size", 14.0)}/2.0,0))")
                appendLine("tool_$suffix = _place(tool_$suffix, $placement)")
                appendLine("body = _require_body(body).fuse(tool_$suffix)")
            }
            BasicCadOperation.COMBINE_SUBTRACT -> {
                appendLine("tool_$suffix = Part.makeCylinder(${n("diameter", 10.0)}/2.0,80.0,App.Vector(0,0,-40.0))")
                appendLine("tool_$suffix = _place(tool_$suffix, $placement)")
                appendLine("body = _require_body(body).cut(tool_$suffix)")
            }
            BasicCadOperation.COMBINE_COMMON -> {
                appendLine("tool_$suffix = Part.makeCylinder(${n("diameter", 28.0)}/2.0,80.0,App.Vector(0,0,-40.0))")
                appendLine("tool_$suffix = _place(tool_$suffix, $placement)")
                appendLine("body = _require_body(body).common(tool_$suffix)")
            }
        }
    }

    private fun StringBuilder.appendSketchExtrusion(
        name: String,
        sketch: CadSketch?,
        depth: String,
        featureParameters: Map<String, Double>
    ) {
        val closed = sketch?.closedPrimitives.orEmpty()
        if (sketch != null) {
            if (closed.isEmpty()) {
                appendLine("raise RuntimeError('La extrusión requiere al menos una región cerrada')")
                return
            }
            val parts = ArrayList<String>(closed.size)
            closed.forEachIndexed { index, primitive ->
                val part = "${name}_region_${index + 1}"
                appendClosedPrimitiveExtrusion(part, primitive, depth)
                parts += part
            }
            appendLine("$name = _fuse_all([${parts.joinToString(",")}])")
            return
        }

        if ("diameter" in featureParameters) {
            appendLine("$name = Part.makeCylinder(${(featureParameters["diameter"] ?: 34.93).pythonNumber()}/2.0,$depth)")
        } else {
            appendLine("$name = Part.makeBox(${(featureParameters["length"] ?: 30.0).pythonNumber()},${(featureParameters["width"] ?: 22.0).pythonNumber()},$depth)")
        }
    }

    private fun StringBuilder.appendClosedPrimitiveExtrusion(
        name: String,
        primitive: CadSketchPrimitive,
        depth: String
    ) {
        val parameters = primitive.parameters
        fun value(key: String, fallback: Double): String = (parameters[key] ?: fallback).pythonNumber()
        when (primitive.kind) {
            CadSketchPrimitiveKind.CIRCLE -> appendLine(
                "$name = Part.makeCylinder(${value("diameter", 20.0)}/2.0,$depth,App.Vector(${value("centerX", 0.0)},${value("centerY", 0.0)},0))"
            )
            CadSketchPrimitiveKind.RECTANGLE -> appendLine(
                "$name = Part.makeBox(${value("width", 30.0)},${value("height", 20.0)},$depth,App.Vector(${value("centerX", 0.0)}-${value("width", 30.0)}/2.0,${value("centerY", 0.0)}-${value("height", 20.0)}/2.0,0))"
            )
            CadSketchPrimitiveKind.POLYGON -> appendLine(
                "$name = _polygon_prism(${value("radius", 12.0)},int(${value("sides", 6.0)}),$depth,${value("centerX", 0.0)},${value("centerY", 0.0)})"
            )
            CadSketchPrimitiveKind.LINE,
            CadSketchPrimitiveKind.ARC -> error("La entidad ${primitive.kind} no define una región cerrada")
        }
    }

    private fun planePlacementArguments(plane: CadReferencePlane): String {
        val origin = plane.origin
        val normal = plane.normalizedNormal
        return listOf(origin.x, origin.y, origin.z, normal.x, normal.y, normal.z)
            .joinToString(",") { it.pythonNumber() }
    }

    private fun pythonString(value: String): String = "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"

    private const val HELPERS = """
def _require_body(value):
    if value is None:
        raise RuntimeError('La operación requiere un cuerpo activo')
    return value

def _place(shape, ox, oy, oz, nx, ny, nz):
    normal = App.Vector(nx, ny, nz)
    if normal.Length <= 1.0e-9:
        raise RuntimeError('Normal de plano inválida')
    normal.normalize()
    rotation = App.Rotation(App.Vector(0,0,1), normal)
    shape.Placement = App.Placement(App.Vector(ox,oy,oz), rotation)
    return shape

def _fuse_all(shapes):
    if not shapes:
        raise ValueError('No hay formas para combinar')
    result = shapes[0]
    for shape in shapes[1:]:
        result = result.fuse(shape)
    return result

def _cylinder_between(a, b, radius):
    direction = b.sub(a)
    if direction.Length <= 1.0e-9:
        return Part.makeSphere(radius, a)
    return Part.makeCylinder(radius, direction.Length, a, direction)

def _pipe_polyline(points, radius):
    pieces = []
    for i in range(len(points)-1):
        pieces.append(_cylinder_between(points[i], points[i+1], radius))
    for point in points[1:-1]:
        pieces.append(Part.makeSphere(radius, point))
    return _fuse_all(pieces)

def _polygon_prism(radius, sides, height, cx=0.0, cy=0.0):
    if sides < 3:
        raise ValueError('Un polígono requiere al menos tres lados')
    points = []
    for i in range(sides):
        angle = 2.0 * math.pi * i / sides
        points.append(App.Vector(cx+radius*math.cos(angle), cy+radius*math.sin(angle), 0))
    points.append(points[0])
    return Part.Face(Part.makePolygon(points)).extrude(App.Vector(0,0,height))

def _rounded_box(length, width, height, radius):
    r = min(radius, length/2.0, width/2.0, height/2.0)
    pieces = [
        Part.makeBox(length-2*r, width, height, App.Vector(-length/2+r,-width/2,0)),
        Part.makeBox(length, width-2*r, height, App.Vector(-length/2,-width/2+r,0)),
        Part.makeBox(length, width, height-2*r, App.Vector(-length/2,-width/2,r)),
    ]
    for x in (-length/2+r, length/2-r):
        for y in (-width/2+r, width/2-r):
            pieces.append(Part.makeCylinder(r,height-2*r,App.Vector(x,y,r)))
            for z in (r,height-r):
                pieces.append(Part.makeSphere(r,App.Vector(x,y,z)))
    return _fuse_all(pieces)

def _chamfered_prism(length, width, height, distance):
    d = min(distance, length/2.0, width/2.0)
    x = length/2.0
    y = width/2.0
    points = [
        App.Vector(-x+d,-y,0), App.Vector(x-d,-y,0), App.Vector(x,-y+d,0),
        App.Vector(x,y-d,0), App.Vector(x-d,y,0), App.Vector(-x+d,y,0),
        App.Vector(-x,y-d,0), App.Vector(-x,-y+d,0), App.Vector(-x+d,-y,0)
    ]
    return Part.Face(Part.makePolygon(points)).extrude(App.Vector(0,0,height))
"""
}
