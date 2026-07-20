package com.example.faceui

import com.example.nativecad.viewer.NativeSceneMesh
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

enum class CadViewportSelectionModeV27(val label: String) { AUTO("Auto"), FACE("Cara"), EDGE("Arista"), VERTEX("Vértice"), LOOP("Bucle") }

sealed interface CadViewportSelectionV27 { val id: String; val point: FloatArray }
data class CadViewportFaceSelectionV27(
    override val id: String, val triangleOrdinals: IntArray, override val point: FloatArray,
    val normal: FloatArray, val planar: Boolean, val approximateArea: Float = 0f, val boundaryLoopCount: Int = 0
) : CadViewportSelectionV27
data class CadViewportEdgeSelectionV27(
    override val id: String, val edgeId: Int, val vertexA: Int, val vertexB: Int,
    val start: FloatArray, val end: FloatArray, override val point: FloatArray,
    val length: Float, val boundary: Boolean, val sharp: Boolean
) : CadViewportSelectionV27
data class CadViewportVertexSelectionV27(
    override val id: String, val vertexIndex: Int, override val point: FloatArray, val incidentFeatureEdges: Int
) : CadViewportSelectionV27
data class CadViewportLoopSelectionV27(
    override val id: String, val edgeIds: IntArray, val orderedPoints: FloatArray,
    override val point: FloatArray, val normal: FloatArray, val closed: Boolean, val perimeter: Float
) : CadViewportSelectionV27

/** Topology inferred from the committed display mesh. Rebuilt only after BRep commit. */
class CadBRepTopologyV27(private val mesh: NativeSceneMesh) {
    private data class Key(val a: Int, val b: Int) { companion object { fun of(a: Int, b: Int) = if (a < b) Key(a,b) else Key(b,a) } }
    private data class Tri(val i: IntArray, val n: FloatArray, val c: FloatArray, val area: Float)
    private data class Edge(val id: Int, val key: Key, val owners: IntArray, val boundary: Boolean, val sharp: Boolean)
    private data class Hit(val tri: Int, val t: Float, val p: FloatArray)
    private data class RayEdge(val distance: Float, val rayT: Float, val point: FloatArray)
    private data class Loop(val edges: IntArray, val vertices: IntArray, val closed: Boolean, val perimeter: Float)

    private val tris = List(mesh.triangleCount) { o ->
        val k=o*3; val ids=intArrayOf(mesh.indices[k],mesh.indices[k+1],mesh.indices[k+2])
        val a=p(ids[0]); val b=p(ids[1]); val c=p(ids[2]); val cr=cross(sub(b,a),sub(c,a))
        Tri(ids, norm(cr), floatArrayOf((a[0]+b[0]+c[0])/3f,(a[1]+b[1]+c[1])/3f,(a[2]+b[2]+c[2])/3f), len(cr)/2f)
    }
    private val owners = buildOwners()
    private val neighbours = buildNeighbours()
    private val surfaceByTri: IntArray
    private val surfaces: List<IntArray>
    private val edges: List<Edge>
    private val featureEdgeIds: IntArray
    private val featureByVertex: Map<Int,IntArray>

    init {
        val built=buildSurfaces(); surfaceByTri=built.first; surfaces=built.second
        edges=owners.entries.sortedWith(compareBy<Map.Entry<Key,IntArray>>{it.key.a}.thenBy{it.key.b}).mapIndexed { id,e ->
            val boundary=e.value.size==1
            val crossSurface=e.value.map{surfaceByTri[it]}.distinct().size>1
            val alignment=if(e.value.size<2)1f else dot(tris[e.value[0]].n,tris[e.value[1]].n)
            Edge(id,e.key,e.value,boundary,crossSurface || alignment<SHARP_DOT)
        }
        featureEdgeIds=edges.filter{it.boundary||it.sharp}.map{it.id}.toIntArray()
        val map=hashMapOf<Int,MutableList<Int>>()
        featureEdgeIds.forEach { id -> val e=edges[id]; map.getOrPut(e.key.a){arrayListOf()}.add(id); map.getOrPut(e.key.b){arrayListOf()}.add(id) }
        featureByVertex=map.mapValues{it.value.sorted().toIntArray()}
    }

    fun pick(rayOrigin: FloatArray, rayDirection: FloatArray): CadViewportFaceSelectionV27? = pickFace(rayOrigin,rayDirection)
    fun pick(origin: FloatArray, direction: FloatArray, mode: CadViewportSelectionModeV27, worldTolerance: Float): CadViewportSelectionV27? {
        val tol=max(worldTolerance,mesh.maxDimension*0.0002f)
        return when(mode){
            CadViewportSelectionModeV27.FACE->pickFace(origin,direction)
            CadViewportSelectionModeV27.EDGE->pickEdge(origin,direction,tol)
            CadViewportSelectionModeV27.VERTEX->pickVertex(origin,direction,tol)
            CadViewportSelectionModeV27.LOOP->pickLoop(origin,direction)
            CadViewportSelectionModeV27.AUTO->pickVertex(origin,direction,tol*.72f)?:pickEdge(origin,direction,tol)?:pickFace(origin,direction)
        }
    }

    fun pickFace(origin: FloatArray, direction: FloatArray): CadViewportFaceSelectionV27? {
        val hit=hit(origin,norm(direction))?:return null; val cluster=surfaces[surfaceByTri[hit.tri]]
        val seed=tris[hit.tri].n; val planar=isPlanar(cluster,hit.p,seed); val normal=if(planar)average(cluster,seed) else seed.copyOf()
        val loops=loops(cluster); val hash=cluster.fold(17){a,v->a*31+v}
        return CadViewportFaceSelectionV27("MeshFace-${hit.tri}-${hash.toUInt().toString(16)}",cluster.copyOf(),hit.p,normal,planar,cluster.sumOf{tris[it].area.toDouble()}.toFloat(),loops.size)
    }

    fun pickEdge(origin: FloatArray, direction: FloatArray, tolerance: Float): CadViewportEdgeSelectionV27? {
        val d=norm(direction); val front=hit(origin,d)?.t?:Float.POSITIVE_INFINITY
        var best: Pair<Edge,RayEdge>?=null
        featureEdgeIds.forEach { id -> val e=edges[id]; val a=p(e.key.a); val b=p(e.key.b); val r=raySegment(origin,d,a,b)
            if(r.rayT>EPS && r.distance<=tolerance && r.rayT<=front+tolerance*3.5f && (best==null||r.distance<best!!.second.distance)) best=e to r }
        val pair=best?:return null; val e=pair.first; val a=p(e.key.a); val b=p(e.key.b)
        return CadViewportEdgeSelectionV27("MeshEdge-${e.id}-${e.key.a}-${e.key.b}",e.id,e.key.a,e.key.b,a,b,pair.second.point,len(sub(b,a)),e.boundary,e.sharp)
    }

    fun pickVertex(origin: FloatArray, direction: FloatArray, tolerance: Float): CadViewportVertexSelectionV27? {
        val d=norm(direction); val front=hit(origin,d)?.t?:Float.POSITIVE_INFINITY
        var best=-1; var bestDist=Float.POSITIVE_INFINITY
        featureByVertex.keys.forEach { v -> val q=p(v); val t=dot(sub(q,origin),d); if(t<=EPS||t>front+tolerance*3.5f)return@forEach
            val dist=len(sub(q,add(origin,mul(d,t)))); if(dist<=tolerance&&dist<bestDist){best=v;bestDist=dist} }
        return if(best<0)null else CadViewportVertexSelectionV27("MeshVertex-$best",best,p(best),featureByVertex[best]?.size?:0)
    }

    fun pickLoop(origin: FloatArray, direction: FloatArray): CadViewportLoopSelectionV27? {
        val face=pickFace(origin,direction)?:return null; val candidates=loops(face.triangleOrdinals); if(candidates.isEmpty())return null
        val loop=candidates.minByOrNull { l -> l.vertices.minOfOrNull { v -> len(sub(p(v),face.point)).toDouble() } ?: Double.MAX_VALUE }?:return null
        val values=FloatArray(loop.vertices.size*3); loop.vertices.forEachIndexed { i,v -> val q=p(v); values[i*3]=q[0];values[i*3+1]=q[1];values[i*3+2]=q[2] }
        return CadViewportLoopSelectionV27("MeshLoop-${loop.edges.joinToString("-")}",loop.edges,values,face.point,face.normal,loop.closed,loop.perimeter)
    }

    internal fun clusterFromSeed(seedOrdinal: Int): IntArray = surfaces[surfaceByTri[seedOrdinal]].copyOf()

    private fun buildOwners(): Map<Key,IntArray> { val m=hashMapOf<Key,MutableList<Int>>(); tris.forEachIndexed{o,t->val i=t.i; arrayOf(Key.of(i[0],i[1]),Key.of(i[1],i[2]),Key.of(i[2],i[0])).forEach{m.getOrPut(it){arrayListOf()}.add(o)}};return m.mapValues{it.value.toIntArray()} }
    private fun buildNeighbours(): Array<IntArray> { val n=Array(tris.size){linkedSetOf<Int>()}; owners.values.forEach{os->os.forEach{a->os.forEach{b->if(a!=b)n[a].add(b)}}};return Array(n.size){n[it].toIntArray()} }
    private fun buildSurfaces(): Pair<IntArray,List<IntArray>> { val ids=IntArray(tris.size){-1};val groups=arrayListOf<IntArray>();tris.indices.forEach{seed->if(ids[seed]<0){val id=groups.size;val q=ArrayDeque<Int>();val g=arrayListOf<Int>();ids[seed]=id;q.add(seed);while(q.isNotEmpty()){val a=q.removeFirst();g.add(a);neighbours[a].forEach{b->if(ids[b]<0&&dot(tris[a].n,tris[b].n)>=SURFACE_DOT){ids[b]=id;q.add(b)}}};groups.add(g.sorted().toIntArray())}};return ids to groups }

    private fun loops(cluster: IntArray): List<Loop> {
        val set=cluster.toHashSet(); val remaining=edges.filter{e->e.owners.count{it in set}==1}.map{it.id}.toMutableSet(); if(remaining.isEmpty())return emptyList()
        val adjacency=hashMapOf<Int,MutableList<Int>>(); remaining.forEach{id->val e=edges[id];adjacency.getOrPut(e.key.a){arrayListOf()}.add(id);adjacency.getOrPut(e.key.b){arrayListOf()}.add(id)}
        val out=arrayListOf<Loop>()
        while(remaining.isNotEmpty()){
            val seed=remaining.minOrNull()!!; val component=linkedSetOf<Int>(); val q=ArrayDeque<Int>();component.add(seed);q.add(seed)
            while(q.isNotEmpty()){val id=q.removeFirst();val e=edges[id];listOf(e.key.a,e.key.b).forEach{v->adjacency[v].orEmpty().forEach{c->if(c in remaining&&component.add(c))q.add(c)}}}
            val vertices=component.flatMap{listOf(edges[it].key.a,edges[it].key.b)}.distinct(); val start=vertices.filter{v->adjacency[v].orEmpty().count{it in component}==1}.minOrNull()?:vertices.minOrNull()!!
            val unused=component.toMutableSet();val orderE=arrayListOf<Int>();val orderV=arrayListOf(start);var current=start
            while(unused.isNotEmpty()){val next=adjacency[current].orEmpty().filter{it in unused}.minOrNull()?:break;unused.remove(next);orderE.add(next);val e=edges[next];current=if(e.key.a==current)e.key.b else e.key.a;orderV.add(current);if(current==start&&unused.isEmpty())break}
            remaining.removeAll(component);if(orderE.isNotEmpty()){val perimeter=orderE.sumOf{id->val e=edges[id];len(sub(p(e.key.a),p(e.key.b))).toDouble()}.toFloat();out.add(Loop(orderE.toIntArray(),orderV.toIntArray(),orderV.size>2&&orderV.first()==orderV.last(),perimeter))}
        }
        return out.sortedByDescending{it.perimeter}
    }

    private fun hit(origin: FloatArray, direction: FloatArray): Hit? { var best=-1;var bt=Float.POSITIVE_INFINITY;tris.forEachIndexed{o,t->val x=intersect(origin,direction,t.i);if(x!=null&&x<bt){best=o;bt=x}};return if(best<0)null else Hit(best,bt,add(origin,mul(direction,bt))) }
    private fun intersect(o:FloatArray,d:FloatArray,idx:IntArray):Float?{val a=p(idx[0]);val b=p(idx[1]);val c=p(idx[2]);val e1=sub(b,a);val e2=sub(c,a);val h=cross(d,e2);val det=dot(e1,h);if(abs(det)<EPS)return null;val inv=1f/det;val s=sub(o,a);val u=dot(s,h)*inv;if(u<0f||u>1f)return null;val q=cross(s,e1);val v=dot(d,q)*inv;if(v<0f||u+v>1f)return null;return (dot(e2,q)*inv).takeIf{it>EPS}}
    private fun raySegment(o:FloatArray,d:FloatArray,a:FloatArray,b:FloatArray):RayEdge{val s=sub(b,a);val w=sub(o,a);val aa=dot(d,d);val bb=dot(d,s);val cc=dot(s,s);val dd=dot(d,w);val ee=dot(s,w);val den=aa*cc-bb*bb;var rt:Float;var st:Float;if(cc<EPS){rt=max(0f,-dd/aa);st=0f}else if(abs(den)<EPS){st=(ee/cc).coerceIn(0f,1f);rt=max(0f,dot(sub(add(a,mul(s,st)),o),d)/aa)}else{rt=(bb*ee-cc*dd)/den;st=(aa*ee-bb*dd)/den;if(rt<0f){rt=0f;st=(ee/cc).coerceIn(0f,1f)}else if(st !in 0f..1f){st=st.coerceIn(0f,1f);rt=max(0f,dot(sub(add(a,mul(s,st)),o),d)/aa)}};val ps=add(a,mul(s,st));return RayEdge(len(sub(add(o,mul(d,rt)),ps)),rt,ps)}
    private fun isPlanar(cluster:IntArray,point:FloatArray,n:FloatArray):Boolean{val tol=mesh.maxDimension*.0002f+1e-5f;return cluster.all{dot(n,tris[it].n)>=.9992f&&abs(dot(n,sub(tris[it].c,point)))<=tol}}
    private fun average(cluster:IntArray,fallback:FloatArray):FloatArray{val s=floatArrayOf(0f,0f,0f);cluster.forEach{val n=tris[it].n;s[0]+=n[0];s[1]+=n[1];s[2]+=n[2]};return if(len(s)<EPS)fallback.copyOf() else norm(s)}
    private fun p(i:Int):FloatArray{val k=i*6;return floatArrayOf(mesh.vertices[k],mesh.vertices[k+1],mesh.vertices[k+2])}

    companion object {
        private const val EPS=1e-7f;private const val SURFACE_DOT=.92f;private const val SHARP_DOT=.985f
        internal fun dot(a:FloatArray,b:FloatArray)=a[0]*b[0]+a[1]*b[1]+a[2]*b[2]
        internal fun cross(a:FloatArray,b:FloatArray)=floatArrayOf(a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0])
        internal fun sub(a:FloatArray,b:FloatArray)=floatArrayOf(a[0]-b[0],a[1]-b[1],a[2]-b[2])
        internal fun add(a:FloatArray,b:FloatArray)=floatArrayOf(a[0]+b[0],a[1]+b[1],a[2]+b[2])
        internal fun mul(a:FloatArray,s:Float)=floatArrayOf(a[0]*s,a[1]*s,a[2]*s)
        internal fun len(a:FloatArray)=sqrt(dot(a,a))
        internal fun norm(a:FloatArray):FloatArray{val l=max(EPS,len(a));return floatArrayOf(a[0]/l,a[1]/l,a[2]/l)}
    }
}
