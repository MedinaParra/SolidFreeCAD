package com.example.nativecad.viewer

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class NativeCadRenderer : GLSurfaceView.Renderer {
    val camera = NativeCameraController()

    @Volatile
    private var pendingMesh: NativeSceneMesh? = null
    private var vertexBuffer: FloatBuffer? = null
    private var indexBuffer: ShortBuffer? = null
    private var indexCount = 0
    private var program = 0
    private var width = 1
    private var height = 1

    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val modelViewMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private val vertexShader = """
        uniform mat4 uMvp;
        attribute vec3 aPosition;
        attribute vec3 aNormal;
        varying vec3 vNormal;
        void main() {
            vNormal = normalize(aNormal);
            gl_Position = uMvp * vec4(aPosition, 1.0);
        }
    """.trimIndent()

    private val fragmentShader = """
        precision mediump float;
        uniform vec4 uColor;
        uniform vec3 uLightDirection;
        varying vec3 vNormal;
        void main() {
            vec3 normal = normalize(vNormal);
            vec3 lightDirection = normalize(uLightDirection);
            float diffuse = max(dot(normal, lightDirection), 0.0);
            float backLight = max(dot(-normal, lightDirection), 0.0) * 0.18;
            float intensity = 0.28 + diffuse * 0.72 + backLight;
            gl_FragColor = vec4(uColor.rgb * intensity, uColor.a);
        }
    """.trimIndent()

    fun setMesh(mesh: NativeSceneMesh) {
        pendingMesh = mesh
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.055f, 0.065f, 0.075f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        program = createProgram(vertexShader, fragmentShader)
        Matrix.setIdentityM(modelMatrix, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, viewportWidth: Int, viewportHeight: Int) {
        width = viewportWidth.coerceAtLeast(1)
        height = viewportHeight.coerceAtLeast(1)
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        pendingMesh?.let { mesh ->
            upload(mesh)
            pendingMesh = null
        }
        if (program == 0 || indexCount <= 0) return

        camera.viewMatrix(viewMatrix)
        camera.projectionMatrix(projectionMatrix, width, height)
        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)

        GLES20.glUseProgram(program)
        val mvpLocation = GLES20.glGetUniformLocation(program, "uMvp")
        val colorLocation = GLES20.glGetUniformLocation(program, "uColor")
        val lightLocation = GLES20.glGetUniformLocation(program, "uLightDirection")
        val positionLocation = GLES20.glGetAttribLocation(program, "aPosition")
        val normalLocation = GLES20.glGetAttribLocation(program, "aNormal")
        if (positionLocation < 0 || normalLocation < 0) return

        GLES20.glUniformMatrix4fv(mvpLocation, 1, false, mvpMatrix, 0)
        GLES20.glUniform4f(colorLocation, 0.22f, 0.61f, 0.86f, 1f)
        GLES20.glUniform3f(lightLocation, 0.45f, 0.75f, 1f)

        val vertices = vertexBuffer ?: return
        val indices = indexBuffer ?: return
        vertices.position(0)
        GLES20.glEnableVertexAttribArray(positionLocation)
        GLES20.glVertexAttribPointer(positionLocation, 3, GLES20.GL_FLOAT, false, 24, vertices)
        vertices.position(3)
        GLES20.glEnableVertexAttribArray(normalLocation)
        GLES20.glVertexAttribPointer(normalLocation, 3, GLES20.GL_FLOAT, false, 24, vertices)
        indices.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indices)
        GLES20.glDisableVertexAttribArray(positionLocation)
        GLES20.glDisableVertexAttribArray(normalLocation)
    }

    fun release() {
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
        vertexBuffer = null
        indexBuffer = null
        indexCount = 0
    }

    private fun upload(mesh: NativeSceneMesh) {
        vertexBuffer = ByteBuffer.allocateDirect(mesh.vertices.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(mesh.vertices)
                position(0)
            }
        indexBuffer = ByteBuffer.allocateDirect(mesh.indices.size * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                put(mesh.indices)
                position(0)
            }
        indexCount = mesh.indices.size
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertex == 0) return 0
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragment == 0) {
            GLES20.glDeleteShader(vertex)
            return 0
        }

        val result = GLES20.glCreateProgram()
        if (result == 0) {
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)
            return 0
        }
        GLES20.glAttachShader(result, vertex)
        GLES20.glAttachShader(result, fragment)
        GLES20.glLinkProgram(result)
        val status = IntArray(1)
        GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, status, 0)
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        if (status[0] == 0) {
            Log.e("NativeCadRenderer", "Error enlazando shaders: ${GLES20.glGetProgramInfoLog(result)}")
            GLES20.glDeleteProgram(result)
            return 0
        }
        return result
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) return 0
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e("NativeCadRenderer", "Error compilando shader: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }
}
