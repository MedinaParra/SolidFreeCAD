package com.example.faceui

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.nativecad.viewer.NativeCameraController
import com.example.nativecad.viewer.NativeSceneMesh
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Adds topology selection and visible planes without replacing GPU/VSYNC direct editing. */
class GpuBRepCadRendererV27 : GLSurfaceView.Renderer {
    private val base = GpuFaceDrivenCadRenderer()
    val camera: NativeCameraController get() = base.camera
    val selectedFace: EditableCadFace get() = base.selectedFace

    @Volatile private var pendingMesh: NativeSceneMesh? = null
    @Volatile private var pendingPlanes: List<CadViewportPlane> = emptyList()
    @Volatile private var planesDirty = false
    private var mesh: NativeSceneMesh? = null
    private var topology: CadBRepTopologyV27? = null
    private var selectionBuffer: FloatBuffer? = null
    private var selectionVertexCount = 0
    private var selectionPrimitive = GLES20.GL_TRIANGLES
    private var selectionLineWidth = 1f
    private var selectionPointSize = 10f
    private var selectionColor = floatArrayOf(1f, .42f, .02f, .94f)
    private var planes: List<PlaneGeometry> = emptyList()
    private var width=1; private var height=1; private var flatProgram=0
    private val model=FloatArray(16); private val view=FloatArray(16); private val projection=FloatArray(16)
    private val modelView=FloatArray(16); private val mvp=FloatArray(16); private val inverseMvp=FloatArray(16)

    private data class PlaneGeometry(
        val id:Long,val origin:FloatArray,val normal:FloatArray,val u:FloatArray,val v:FloatArray,
        val halfSize:Float,val active:Boolean,val fill:FloatBuffer,val outline:FloatBuffer
    )
    private val vertexShader="""
        uniform mat4 uMvp;
        attribute vec3 aPosition;
        uniform float uPointSize;
        void main(){ gl_Position=uMvp*vec4(aPosition,1.0); gl_PointSize=uPointSize; }
    """.trimIndent()
    private val fragmentShader="""
        precision mediump float;
        uniform vec4 uColor;
        void main(){ gl_FragColor=uColor; }
    """.trimIndent()

    fun setMesh(value:NativeSceneMesh){ pendingMesh=value; base.setMesh(value) }
    fun setSelectedFace(face:EditableCadFace)=base.setSelectedFace(face)
    fun setLivePreview(length:Float,diameter:Float)=base.setLivePreview(length,diameter)
    fun clearLivePreview()=base.clearLivePreview()
    fun worldPerPixel():Float=base.worldPerPixel()
    fun pickFace(x:Float,y:Float):EditableCadFace=base.pickFace(x,y)
    fun manipulatorScreenVector(face:EditableCadFace):FloatArray?=base.manipulatorScreenVector(face)
    fun hitManipulator(x:Float,y:Float,radius:Float):Boolean=base.hitManipulator(x,y,radius)
    fun setReferencePlanes(value:List<CadViewportPlane>){ pendingPlanes=value.map{it.copy(origin=it.origin.copyOf(),normal=it.normal.copyOf())};planesDirty=true }

    @Synchronized fun clearGenericSelection(){ selectionBuffer=null;selectionVertexCount=0;selectionPrimitive=GLES20.GL_TRIANGLES }
    @Synchronized fun pickTopology(x:Float,y:Float,mode:CadViewportSelectionModeV27):CadViewportSelectionV27?{
        val ray=selectionRay(x,y)?:return null
        val tolerance=(base.worldPerPixel()*PICK_RADIUS_PX).coerceAtLeast((mesh?.maxDimension?:1f)*2e-4f)
        return topology?.pick(ray.first,ray.second,mode,tolerance).also(::rebuildSelectionBuffer)
    }
    @Synchronized fun pickReferencePlane(x:Float,y:Float):Long?{
        val ray=selectionRay(x,y)?:return null;var id:Long?=null;var best=Float.POSITIVE_INFINITY
        planes.forEach{plane->
            val den=CadBRepTopologyV27.dot(plane.normal,ray.second);if(abs(den)<EPSILON)return@forEach
            val distance=CadBRepTopologyV27.dot(plane.normal,CadBRepTopologyV27.sub(plane.origin,ray.first))/den
            if(distance<=0f||distance>=best)return@forEach
            val hit=floatArrayOf(ray.first[0]+ray.second[0]*distance,ray.first[1]+ray.second[1]*distance,ray.first[2]+ray.second[2]*distance)
            val local=CadBRepTopologyV27.sub(hit,plane.origin)
            if(abs(CadBRepTopologyV27.dot(local,plane.u))<=plane.halfSize&&abs(CadBRepTopologyV27.dot(local,plane.v))<=plane.halfSize){id=plane.id;best=distance}
        };return id
    }

    override fun onSurfaceCreated(gl:GL10?,config:EGLConfig?){base.onSurfaceCreated(gl,config);flatProgram=createProgram(vertexShader,fragmentShader);Matrix.setIdentityM(model,0)}
    override fun onSurfaceChanged(gl:GL10?,w:Int,h:Int){base.onSurfaceChanged(gl,w,h);width=w.coerceAtLeast(1);height=h.coerceAtLeast(1)}
    @Synchronized override fun onDrawFrame(gl:GL10?){
        base.onDrawFrame(gl)
        pendingMesh?.let{mesh=it;topology=CadBRepTopologyV27(it);clearGenericSelection();rebuildPlanes(pendingPlanes);pendingMesh=null}
        if(planesDirty){rebuildPlanes(pendingPlanes);planesDirty=false}
        updateMvp();drawPlanes();drawSelection()
    }
    fun release(){base.release();if(flatProgram!=0)GLES20.glDeleteProgram(flatProgram);flatProgram=0;clearGenericSelection();planes=emptyList();topology=null;mesh=null}

    private fun updateMvp(){camera.viewMatrix(view);camera.projectionMatrix(projection,width,height);Matrix.multiplyMM(modelView,0,view,0,model,0);Matrix.multiplyMM(mvp,0,projection,0,modelView,0)}
    private fun selectionRay(x:Float,y:Float):Pair<FloatArray,FloatArray>?{updateMvp();if(!Matrix.invertM(inverseMvp,0,mvp,0))return null;val near=unproject(x,y,-1f)?:return null;val far=unproject(x,y,1f)?:return null;return near to CadBRepTopologyV27.norm(CadBRepTopologyV27.sub(far,near))}
    private fun unproject(x:Float,y:Float,z:Float):FloatArray?{val input=floatArrayOf(2f*x/width-1f,1f-2f*y/height,z,1f);val output=FloatArray(4);Matrix.multiplyMV(output,0,inverseMvp,0,input,0);if(abs(output[3])<EPSILON)return null;return floatArrayOf(output[0]/output[3],output[1]/output[3],output[2]/output[3])}

    private fun rebuildSelectionBuffer(selected:CadViewportSelectionV27?){
        val current=mesh;selectionBuffer=null;selectionVertexCount=0;selectionPrimitive=GLES20.GL_TRIANGLES;selectionLineWidth=1f;selectionPointSize=10f;selectionColor=floatArrayOf(1f,.42f,.02f,.94f)
        if(selected==null||current==null)return
        when(selected){
            is CadViewportFaceSelectionV27->{val ord=selected.triangleOrdinals.take(MAX_SELECTION_TRIANGLES);val values=FloatArray(ord.size*9);var c=0;ord.forEach{o->val k=o*3;if(k+2>=current.indices.size)return@forEach;repeat(3){corner->val v=current.indices[k+corner]*6;values[c++]=current.vertices[v];values[c++]=current.vertices[v+1];values[c++]=current.vertices[v+2]}};selectionBuffer=floatBuffer(values.copyOf(c));selectionVertexCount=c/3}
            is CadViewportEdgeSelectionV27->{selectionBuffer=floatBuffer(selected.start+selected.end);selectionVertexCount=2;selectionPrimitive=GLES20.GL_LINES;selectionLineWidth=5f;selectionColor=floatArrayOf(1f,.72f,.02f,1f)}
            is CadViewportVertexSelectionV27->{selectionBuffer=floatBuffer(selected.point.copyOf());selectionVertexCount=1;selectionPrimitive=GLES20.GL_POINTS;selectionPointSize=16f;selectionColor=floatArrayOf(1f,.18f,.04f,1f)}
            is CadViewportLoopSelectionV27->{val src=selected.orderedPoints;val count=src.size/3;if(count<2)return;val values=FloatArray((count-1)*6);var c=0;for(i in 0 until count-1){val a=i*3;val b=(i+1)*3;values[c++]=src[a];values[c++]=src[a+1];values[c++]=src[a+2];values[c++]=src[b];values[c++]=src[b+1];values[c++]=src[b+2]};selectionBuffer=floatBuffer(values);selectionVertexCount=values.size/3;selectionPrimitive=GLES20.GL_LINES;selectionLineWidth=5f;selectionColor=floatArrayOf(.95f,.16f,.68f,1f)}
        }
    }

    private fun rebuildPlanes(source:List<CadViewportPlane>){
        val half=((mesh?.maxDimension?:100f)*.62f).coerceIn(8f,1_000_000f)
        planes=source.filter{it.visible}.mapNotNull{plane->
            val normal=runCatching{CadBRepTopologyV27.norm(plane.normal)}.getOrNull()?:return@mapNotNull null
            val helper=if(abs(normal[2])<.85f)floatArrayOf(0f,0f,1f) else floatArrayOf(0f,1f,0f)
            val u=CadBRepTopologyV27.norm(CadBRepTopologyV27.cross(helper,normal));val v=CadBRepTopologyV27.norm(CadBRepTopologyV27.cross(normal,u))
            fun corner(su:Float,sv:Float)=floatArrayOf(plane.origin[0]+u[0]*half*su+v[0]*half*sv,plane.origin[1]+u[1]*half*su+v[1]*half*sv,plane.origin[2]+u[2]*half*su+v[2]*half*sv)
            val a=corner(-1f,-1f);val b=corner(1f,-1f);val c=corner(1f,1f);val d=corner(-1f,1f)
            PlaneGeometry(plane.id,plane.origin.copyOf(),normal,u,v,half,plane.active,floatBuffer(a+b+c+a+c+d),floatBuffer(a+b+b+c+c+d+d+a))
        }
    }
    private fun drawPlanes(){if(planes.isEmpty())return;GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glDisable(GLES20.GL_CULL_FACE);GLES20.glDepthMask(false);planes.forEach{p->val c=if(p.active)floatArrayOf(.08f,.48f,.92f)else floatArrayOf(.16f,.62f,.78f);drawFlat(p.fill,6,GLES20.GL_TRIANGLES,c[0],c[1],c[2],if(p.active).22f else .10f,1f);drawFlat(p.outline,8,GLES20.GL_LINES,c[0],c[1],c[2],if(p.active).95f else .62f,if(p.active)3f else 1.5f)};GLES20.glDepthMask(true);GLES20.glEnable(GLES20.GL_CULL_FACE)}
    private fun drawSelection(){val buffer=selectionBuffer?:return;if(selectionVertexCount<=0)return;GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glDisable(GLES20.GL_CULL_FACE);GLES20.glDepthMask(false);if(selectionPrimitive==GLES20.GL_TRIANGLES){GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL);GLES20.glPolygonOffset(-1f,-1f)};val c=selectionColor;drawFlat(buffer,selectionVertexCount,selectionPrimitive,c[0],c[1],c[2],c[3],selectionLineWidth);GLES20.glDepthMask(true);if(selectionPrimitive==GLES20.GL_TRIANGLES)GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL);GLES20.glEnable(GLES20.GL_CULL_FACE)}
    private fun drawFlat(buffer:FloatBuffer,count:Int,mode:Int,r:Float,g:Float,b:Float,a:Float,lineWidth:Float){if(flatProgram==0||count<=0)return;GLES20.glUseProgram(flatProgram);val m=GLES20.glGetUniformLocation(flatProgram,"uMvp");val color=GLES20.glGetUniformLocation(flatProgram,"uColor");val pos=GLES20.glGetAttribLocation(flatProgram,"aPosition");if(pos<0)return;GLES20.glUniformMatrix4fv(m,1,false,mvp,0);GLES20.glUniform4f(color,r,g,b,a);GLES20.glUniform1f(GLES20.glGetUniformLocation(flatProgram,"uPointSize"),if(mode==GLES20.GL_POINTS)selectionPointSize else 1f);GLES20.glLineWidth(lineWidth);buffer.position(0);GLES20.glEnableVertexAttribArray(pos);GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,12,buffer);GLES20.glDrawArrays(mode,0,count);GLES20.glDisableVertexAttribArray(pos)}
    private fun floatBuffer(values:FloatArray):FloatBuffer=ByteBuffer.allocateDirect(values.size*Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(values);position(0)}
    private fun createProgram(vs:String,fs:String):Int{fun compile(type:Int,source:String):Int{val shader=GLES20.glCreateShader(type);GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader);val status=IntArray(1);GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,status,0);if(status[0]==0){GLES20.glDeleteShader(shader);return 0};return shader};val v=compile(GLES20.GL_VERTEX_SHADER,vs);val f=compile(GLES20.GL_FRAGMENT_SHADER,fs);if(v==0||f==0)return 0;val p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);val status=IntArray(1);GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,status,0);if(status[0]==0){GLES20.glDeleteProgram(p);return 0};return p}
    private companion object{const val EPSILON=1e-7f;const val MAX_SELECTION_TRIANGLES=200_000;const val PICK_RADIUS_PX=18f}
}
