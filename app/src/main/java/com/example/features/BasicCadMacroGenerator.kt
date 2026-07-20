package com.example.features

/**
 * Builds a FreeCAD-compatible macro from the semantic feature, sketch and plane lists.
 *
 * Sketch-driven extrusions are emitted in the local coordinate frame of their reference
 * plane. This keeps principal planes, offset planes and planes captured from model faces
 * consistent with the tree and the graphical editor.
 */
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
                appendLine("# ${feature.label}: ${feature.operation.description}")
                appendFeature(program, feature, index)
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
        program: BasicCadProgram,
        feature: BasicCadFeature,
        index: Int
    ) {
        val p = feature.parameters
        fun n(key: String, fallback: Double): String = (p[key] ?: fallback).pythonNumber()
        val suffix = index + 1
        val sketch = program.sketch(feature.sketchId)
        val plane = program.plane(feature.planeId ?: sketch?.planeId) ?: program.activePlane()
        val frame = plane.frame()

        when (feature.operation) {
            BasicCadOperation.BOSS_EXTRUDE -> {
                appendExtrudedProfile(
                    shapeName = "feature_$suffix",
                    sketch = sketch,
                    feature = feature,
                    plane = plane,
                    depth = p["depth"] ?: 12.0,
                    fallbackLength = p["length"] ?: 30.0,
                    fallbackWidth = p["width"] ?: 22.0
                )
                appendLine("body = feature_$suffix if body is None else body.fuse(feature_$suffix)")
            }

            BasicCadOperation.CUT_EXTRUDE -> {
                appendExtrudedProfile(
                    shapeName = "tool_$suffix",
                    sketch = sketch,
                    feature = feature,
                    plane = plane,
                    depth = p["depth"] ?: 50.0,
                    fallbackLength = p["length"] ?: 12.0,
                    fallbackWidth = p["width"] ?: 8.0,
                    reverse = true
                )
                appendLine("body = _require_body(body).cut(tool_$suffix)")
            }

            BasicCadOperation.BOSS_REVOLVE -> {
                appendLine("feature_$suffix = Part.makeTorus(${n("majorRadius", 24.0)}, ${n("minorRadius", 4.0)}, ${frame.origin.pythonVector()}, ${frame.normal.pythonVector()})")
                appendLine("body = _require_body(body).fuse(feature_$suffix)")
            }

            BasicCadOperation.CUT_REVOLVE -> {
                appendLine("tool_$suffix = Part.makeTorus(${n("majorRadius", 15.0)}, ${n("minorRadius", 2.2)}, ${frame.origin.pythonVector()}, ${frame.normal.pythonVector()})")
                appendLine("body = _require_body(body).cut(tool_$suffix)")
            }

            BasicCadOperation.BOSS_SWEEP -> {
                val a = frame.origin - frame.xAxis * ((p["length"] ?: 38.0) / 2.0)
                val b = frame.origin + frame.normal * (p["rise"] ?: 18.0)
                val c = frame.origin + frame.xAxis * ((p["length"] ?: 38.0) / 2.0)
                appendLine("path_$suffix = [${a.pythonVector()}, ${b.pythonVector()}, ${c.pythonVector()}]")
                appendLine("feature_$suffix = _pipe_polyline(path_$suffix, ${n("radius", 2.5)})")
                appendLine("body = _require_body(body).fuse(feature_$suffix)")
            }

            BasicCadOperation.CUT_SWEEP -> {
                val a = frame.origin - frame.xAxis * ((p["length"] ?: 45.0) / 2.0)
                val b = frame.origin + frame.normal * (p["rise"] ?: 12.0)
                val c = frame.origin + frame.xAxis * ((p["length"] ?: 45.0) / 2.0)
                appendLine("path_$suffix = [${a.pythonVector()}, ${b.pythonVector()}, ${c.pythonVector()}]")
                appendLine("tool_$suffix = _pipe_polyline(path_$suffix, ${n("radius", 1.8)})")
                appendLine("body = _require_body(body).cut(tool_$suffix)")
            }

            BasicCadOperation.BOSS_LOFT -> {
                appendLine("feature_$suffix = Part.makeCone(${n("radius1", 12.0)}, ${n("radius2", 6.0)}, ${n("height", 20.0)}, ${frame.origin.pythonVector()}, ${frame.normal.pythonVector()})")
                appendLine("body = _require_body(body).fuse(feature_$suffix)")
            }

            BasicCadOperation.CUT_LOFT -> {
                appendLine("tool_$suffix = Part.makeCone(${n("radius1", 7.0)}, ${n("radius2", 3.0)}, ${n("height", 50.0)}, ${frame.origin.pythonVector()}, ${frame.normal.pythonVector()})")
                appendLine("body = _require_body(body).cut(tool_$suffix)")
            }

            BasicCadOperation.FILLET -> appendLine(
                "body = _rounded_box(${n("length", 42.0)}, ${n("width", 30.0)}, ${n("height", 18.0)}, ${n("radius", 3.0)})"
            )

            BasicCadOperation.CHAMFER -> appendLine(
                "body = _chamfered_prism(${n("length", 42.0)}, ${n("width", 30.0)}, ${n("height", 18.0)}, ${n("distance", 4.0)})"
            )

            BasicCadOperation.SHELL -> {
                appendLine("outer_$suffix = Part.makeCylinder(${n("radius", 18.0)}, ${n("height", 32.0)}, ${frame.origin.pythonVector()}, ${frame.normal.pythonVector()})")
                val innerOrigin = frame.origin + frame.normal * (p["thickness"] ?: 2.5)
                appendLine("inner_$suffix = Part.makeCylinder(${n("radius", 18.0)} - ${n("thickness", 2.5)}, ${n("height", 32.0)}, ${innerOrigin.pythonVector()}, ${frame.normal.pythonVector()})")
                appendLine("body = outer_$suffix.cut(inner_$suffix)")
            }

            BasicCadOperation.DRAFT -> appendLine(
                "body = Part.makeCone(${n("radius1", 18.0)}, ${n("radius2", 14.0)}, ${n("height", 30.0)}, ${frame.origin.pythonVector()}, ${frame.normal.pythonVector()})"
            )

            BasicCadOperation.RIB -> {
                val half = (p["length"] ?: 28.0) / 2.0
                val a = frame.origin - frame.xAxis * half
                val b = frame.origin + frame.xAxis * half
                val c = frame.origin + frame.normal * (p["height"] ?: 18.0)
                val extrusion = frame.yAxis * (p["thickness"] ?: 3.0)
                appendLine("points_$suffix = [${a.pythonVector()}, ${b.pythonVector()}, ${c.pythonVector()}, ${a.pythonVector()}]")
                appendLine("feature_$suffix = Part.Face(Part.makePolygon(points_$suffix)).extrude(${extrusion.pythonVector()})")
                appendLine("body = _require_body(body).fuse(feature_$suffix)")
            }

            BasicCadOperation.SIMPLE_HOLE -> {
                val start = frame.origin - frame.normal * 10.0
                appendLine("tool_$suffix = Part.makeCylinder(${n("diameter", 8.0)}/2.0, ${n("depth", 80.0)}, ${start.pythonVector()}, ${frame.normal.pythonVector()})")
                appendLine("body = _require_body(body).cut(tool_$suffix)")
            }

            BasicCadOperation.COUNTERBORE_HOLE -> {
                val start = frame.origin - frame.normal * 10.0
                appendLine("through_$suffix = Part.makeCylinder(${n("diameter", 7.0)}/2.0, 80.0, ${start.pythonVector()}, ${frame.normal.pythonVector()})")
                appendLine("seat_$suffix = Part.makeCylinder(${n("counterboreDiameter", 13.0)}/2.0, ${n("counterboreDepth", 5.0)}, ${frame.origin.pythonVector()}, ${frame.normal.pythonVector()})")
                appendLine("body = _require_body(body).cut(through_$suffix.fuse(seat_$suffix))")
            }

            BasicCadOperation.COUNTERSINK_HOLE -> {
                val start = frame.origin - frame.normal * 10.0
                appendLine("through_$suffix = Part.makeCylinder(${n("diameter", 7.0)}/2.0, 80.0, ${start.pythonVector()}, ${frame.normal.pythonVector()})")
                appendLine("sink_$suffix = Part.makeCone(${n("sinkDiameter", 14.0)}/2.0, ${n("diameter", 7.0)}/2.0, ${n("sinkDepth", 4.0)}, ${frame.origin.pythonVector()}, ${frame.normal.pythonVector()})")
                appendLine("body = _require_body(body).cut(through_$suffix.fuse(sink_$suffix))")
            }

            BasicCadOperation.LINEAR_PATTERN -> {
                appendLine("instances_$suffix = []")
                appendLine("for i in range(int(${n("count", 4.0)})):")
                appendLine("    d = (i - (int(${n("count", 4.0)})-1)/2.0) * ${n("spacing", 14.0)}")
                appendLine("    p = _plane_point(${frame.origin.pythonVector()}, ${frame.xAxis.pythonVector()}, ${frame.yAxis.pythonVector()}, d, 0.0)")
                appendLine("    instances_$suffix.append(Part.makeCylinder(${n("diameter", 5.0)}/2.0, 8.0, p, ${frame.normal.pythonVector()}))")
                appendLine("body = _require_body(body).fuse(_fuse_all(instances_$suffix))")
            }

            BasicCadOperation.CIRCULAR_PATTERN -> {
                appendLine("instances_$suffix = []")
                appendLine("for i in range(int(${n("count", 6.0)})):")
                appendLine("    a = 2.0 * math.pi * i / int(${n("count", 6.0)})")
                appendLine("    p = _plane_point(${frame.origin.pythonVector()}, ${frame.xAxis.pythonVector()}, ${frame.yAxis.pythonVector()}, ${n("radius", 14.0)}*math.cos(a), ${n("radius", 14.0)}*math.sin(a))")
                appendLine("    instances_$suffix.append(Part.makeCylinder(${n("diameter", 4.0)}/2.0, 8.0, p, ${frame.normal.pythonVector()}))")
                appendLine("body = _require_body(body).fuse(_fuse_all(instances_$suffix))")
            }

            BasicCadOperation.MIRROR -> {
                val left = frame.origin - frame.xAxis * (p["offset"] ?: 18.0)
                val right = frame.origin + frame.xAxis * (p["offset"] ?: 18.0)
                appendLine("left_$suffix = Part.makeCylinder(${n("diameter", 6.0)}/2.0, 10.0, ${left.pythonVector()}, ${frame.normal.pythonVector()})")
                appendLine("right_$suffix = Part.makeCylinder(${n("diameter", 6.0)}/2.0, 10.0, ${right.pythonVector()}, ${frame.normal.pythonVector()})")
                appendLine("body = _require_body(body).fuse(left_$suffix.fuse(right_$suffix))")
            }

            BasicCadOperation.MOVE_COPY_BODY -> {
                appendLine("copy_$suffix = _require_body(body).copy()")
                appendLine("copy_$suffix.translate(App.Vector(${n("dx", 45.0)}, ${n("dy", 0.0)}, ${n("dz", 0.0)}))")
                appendLine("body = body.fuse(copy_$suffix)")
            }

            BasicCadOperation.COMBINE_ADD -> {
                appendLine("tool_$suffix = Part.makeBox(${n("size", 14.0)}, ${n("size", 14.0)}, ${n("size", 14.0)}, App.Vector(-${n("size", 14.0)}/2.0, -${n("size", 14.0)}/2.0, _top_z(body)-${n("size", 14.0)}/2.0))")
                appendLine("body = _require_body(body).fuse(tool_$suffix)")
            }

            BasicCadOperation.COMBINE_SUBTRACT -> {
                appendLine("tool_$suffix = Part.makeCylinder(${n("diameter", 10.0)}/2.0, 80.0, App.Vector(0,0,-10.0))")
                appendLine("body = _require_body(body).cut(tool_$suffix)")
            }

            BasicCadOperation.COMBINE_COMMON -> {
                appendLine("tool_$suffix = Part.makeCylinder(${n("diameter", 28.0)}/2.0, 80.0, App.Vector(0,0,-10.0))")
                appendLine("body = _require_body(body).common(tool_$suffix)")
            }
        }
    }

    private fun StringBuilder.appendExtrudedProfile(
        shapeName: String,
        sketch: CadSketch?,
        feature: BasicCadFeature,
        plane: CadReferencePlane,
        depth: Double,
        fallbackLength: Double,
        fallbackWidth: Double,
        reverse: Boolean = false
    ) {
        val frame = plane.frame()
        val direction = frame.normal * if (reverse) -depth else depth
        val origin = if (reverse) frame.origin + frame.normal * depth else frame.origin
        val actualSketch = sketch

        when (actualSketch?.profileType) {
            CadSketchProfileType.CIRCLE -> {
                val diameter = actualSketch.parameters["diameter"]
                    ?: feature.parameters["diameter"]
                    ?: 30.0
                appendLine("$shapeName = Part.makeCylinder(${diameter.pythonNumber()}/2.0, ${depth.pythonNumber()}, ${origin.pythonVector()}, ${(if (reverse) frame.normal * -1.0 else frame.normal).pythonVector()})")
            }

            CadSketchProfileType.RECTANGLE -> {
                val width = actualSketch.parameters["width"] ?: fallbackLength
                val height = actualSketch.parameters["height"] ?: fallbackWidth
                appendPolygonExtrusion(shapeName, rectanglePoints(frame.copy(origin = origin), width, height), direction)
            }

            CadSketchProfileType.SLOT -> {
                val length = actualSketch.parameters["length"] ?: fallbackLength
                val width = actualSketch.parameters["width"] ?: fallbackWidth
                val radius = width / 2.0
                val straight = (length - width).coerceAtLeast(0.0)
                val centerA = origin - frame.xAxis * (straight / 2.0)
                val centerB = origin + frame.xAxis * (straight / 2.0)
                val axis = if (reverse) frame.normal * -1.0 else frame.normal
                appendLine("${shapeName}_a = Part.makeCylinder(${radius.pythonNumber()}, ${depth.pythonNumber()}, ${centerA.pythonVector()}, ${axis.pythonVector()})")
                appendLine("${shapeName}_b = Part.makeCylinder(${radius.pythonNumber()}, ${depth.pythonNumber()}, ${centerB.pythonVector()}, ${axis.pythonVector()})")
                appendPolygonExtrusion("${shapeName}_middle", rectanglePoints(frame.copy(origin = origin), straight, width), direction)
                appendLine("$shapeName = ${shapeName}_a.fuse(${shapeName}_middle).fuse(${shapeName}_b)")
            }

            CadSketchProfileType.POLYGON -> {
                val diameter = actualSketch.parameters["diameter"] ?: fallbackLength
                val sides = (actualSketch.parameters["sides"] ?: 6.0).toInt().coerceIn(3, 64)
                val shiftedPlane = plane.copy(origin = origin)
                appendPolygonExtrusion(shapeName, regularPolygonPoints(shiftedPlane, diameter, sides), direction)
            }

            CadSketchProfileType.LINE,
            CadSketchProfileType.ARC -> {
                appendLine("raise RuntimeError('El croquis ${actualSketch.label} debe ser cerrado para extruir')")
            }

            null -> {
                if ("diameter" in feature.parameters) {
                    val diameter = feature.parameters["diameter"] ?: 34.93
                    val axis = if (reverse) frame.normal * -1.0 else frame.normal
                    appendLine("$shapeName = Part.makeCylinder(${diameter.pythonNumber()}/2.0, ${depth.pythonNumber()}, ${origin.pythonVector()}, ${axis.pythonVector()})")
                } else {
                    appendPolygonExtrusion(shapeName, rectanglePoints(frame.copy(origin = origin), fallbackLength, fallbackWidth), direction)
                }
            }
        }
    }

    private fun StringBuilder.appendPolygonExtrusion(
        shapeName: String,
        points: List<CadVector3>,
        direction: CadVector3
    ) {
        val closed = points + points.first()
        appendLine("${shapeName}_points = [${closed.joinToString(", ") { it.pythonVector() }}]")
        appendLine("$shapeName = Part.Face(Part.makePolygon(${shapeName}_points)).extrude(${direction.pythonVector()})")
    }

    private fun rectanglePoints(frame: CadFaceFrame, width: Double, height: Double): List<CadVector3> {
        val halfW = width / 2.0
        val halfH = height / 2.0
        return listOf(
            frame.origin - frame.xAxis * halfW - frame.yAxis * halfH,
            frame.origin + frame.xAxis * halfW - frame.yAxis * halfH,
            frame.origin + frame.xAxis * halfW + frame.yAxis * halfH,
            frame.origin - frame.xAxis * halfW + frame.yAxis * halfH
        )
    }

    private fun pythonString(value: String): String = "'" + value
        .replace("\\", "\\\\")
        .replace("'", "\\'") + "'"

    private const val HELPERS = """
def _require_body(value):
    if value is None:
        raise RuntimeError('La operación requiere un cuerpo activo')
    return value

def _top_z(value):
    # El runtime móvil no expone BoundBox todavía. El cuerpo inicial usa 40 mm.
    return 40.0

def _plane_point(origin, x_axis, y_axis, x, y):
    return App.Vector(
        origin.x + x_axis.x*x + y_axis.x*y,
        origin.y + x_axis.y*x + y_axis.y*y,
        origin.z + x_axis.z*x + y_axis.z*y
    )

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

def _rounded_box(length, width, height, radius):
    r = min(radius, length/2.0, width/2.0, height/2.0)
    pieces = [
        Part.makeBox(length-2*r, width, height, App.Vector(-length/2+r, -width/2, 0)),
        Part.makeBox(length, width-2*r, height, App.Vector(-length/2, -width/2+r, 0)),
        Part.makeBox(length, width, height-2*r, App.Vector(-length/2, -width/2, r)),
    ]
    for x in (-length/2+r, length/2-r):
        for y in (-width/2+r, width/2-r):
            pieces.append(Part.makeCylinder(r, height-2*r, App.Vector(x,y,r)))
            for z in (r, height-r):
                pieces.append(Part.makeSphere(r, App.Vector(x,y,z)))
    for y in (-width/2+r, width/2-r):
        for z in (r, height-r):
            pieces.append(Part.makeCylinder(r, length-2*r, App.Vector(-length/2+r,y,z), App.Vector(1,0,0)))
    for x in (-length/2+r, length/2-r):
        for z in (r, height-r):
            pieces.append(Part.makeCylinder(r, width-2*r, App.Vector(x,-width/2+r,z), App.Vector(0,1,0)))
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
