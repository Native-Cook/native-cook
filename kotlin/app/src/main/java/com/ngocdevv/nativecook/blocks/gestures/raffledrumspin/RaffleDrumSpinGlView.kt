package com.ngocdevv.nativecook.blocks.gestures.raffledrumspin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig as AndroidEglConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30 as GL
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.MotionEvent
import android.view.TextureView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Two instances are needed: the normal pass and a foil pass above the rack-focus layer. */
internal class RaffleDrumSpinGlView(context: Context, foil: Boolean = false) : TextureView(context), TextureView.SurfaceTextureListener {
    private val drumRenderer = RaffleDrumSpinGlRenderer(context.applicationContext, foil)
    private val thread = HandlerThread("RaffleDrumSpin-${if (foil) "foil" else "drum"}").apply { start() }
    private val handler = Handler(thread.looper)
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var released = false
    @Volatile private var presentedTime = Float.NaN
    @Volatile private var requestedTime = Float.NaN
    fun hasPresented(time: Float): Boolean = presentedTime == time
    init {
        isOpaque = false
        isClickable = false
        isFocusable = false
        surfaceTextureListener = this
    }
    fun present(frame: RdsFrame, referenceWidth: Float, referenceHeight: Float) {
        requestedTime = frame.t
        drumRenderer.present(frame, referenceWidth, referenceHeight)
        handler.removeCallbacks(draw)
        handler.post(draw)
    }
    private val draw = Runnable {
        if (surface != EGL14.EGL_NO_SURFACE && !released) {
            drumRenderer.onDrawFrame(null)
            if (!drumRenderer.frameChanged || EGL14.eglSwapBuffers(display, surface)) presentedTime = requestedTime
        }
    }
    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        handler.post {
            if (released) return@post
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0))
            val configs = arrayOfNulls<AndroidEglConfig>(1)
            val count=IntArray(1)
            fun attributes(multisample: Boolean): IntArray {
                val base = mutableListOf(EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_DEPTH_SIZE,24,EGL14.EGL_RENDERABLE_TYPE,0x40)
                if (multisample) base.addAll(listOf(EGL14.EGL_SAMPLE_BUFFERS,1,EGL14.EGL_SAMPLES,4))
                base.add(EGL14.EGL_NONE)
                return base.toIntArray()
            }
            var found=EGL14.eglChooseConfig(display,attributes(true),0,configs,0,1,count,0) && count[0]>0
            if (!found) found=EGL14.eglChooseConfig(display,attributes(false),0,configs,0,1,count,0) && count[0]>0
            check(found)
            eglContext=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE),0)
            surface=EGL14.eglCreateWindowSurface(display,configs[0],texture,intArrayOf(EGL14.EGL_NONE),0)
            check(EGL14.eglMakeCurrent(display,surface,surface,eglContext))
            drumRenderer.onSurfaceCreated(null,null)
            drumRenderer.onSurfaceChanged(null,width,height)
            draw.run()
        }
    }
    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) { handler.post { if (surface != EGL14.EGL_NO_SURFACE) { drumRenderer.onSurfaceChanged(null,width,height); draw.run() } } }
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        return !handler.post { destroySurface(); texture.release() }
    }
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    fun onPause() { handler.post { released=true; destroySurface(); thread.quitSafely() } }
    private fun destroySurface() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            if(surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display,surface)
            if(eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display,eglContext)
            // EGLDisplay is shared by the two TextureViews and by a replacement activity.
            // Destroy only resources owned by this view; terminating the display invalidates
            // the other view's context and can blank a newly launched pinned frame.
        }
        display=EGL14.EGL_NO_DISPLAY; surface=EGL14.EGL_NO_SURFACE; eglContext=EGL14.EGL_NO_CONTEXT
    }
    override fun dispatchTouchEvent(event: MotionEvent?): Boolean = false
    override fun onTouchEvent(event: MotionEvent?): Boolean = false
}

private class RaffleDrumSpinGlRenderer(private val context: Context, private val foil: Boolean) : GLSurfaceView.Renderer {
    private data class State(val frame: RdsFrame, val width: Float, val height: Float)
    private val state = AtomicReference<State?>()
    private val positions = FloatArray(12)
    private val rotations = FloatArray(12)
    private val effects = FloatArray(12)
    private var drumProgram = 0
    private var ballProgram = 0
    private var foilProgram = 0
    private var meshBuffer = 0
    private var vertexCount = 0
    private var envTexture = 0
    private var foilTexture = 0
    private var envLoaded = false
    private var foilLoaded = false
    var frameChanged = true
        private set
    private var emptyFoil = false
    private var screenWidth = 1
    private var screenHeight = 1
    fun present(frame: RdsFrame, width: Float, height: Float) { state.set(State(frame, width, height)) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GL.glClearColor(0f, 0f, 0f, 0f)
        GL.glEnable(GL.GL_BLEND)
        GL.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA)
        GL.glEnable(GL.GL_DEPTH_TEST)
        GL.glDepthFunc(GL.GL_LESS)
        if (foil) {
            foilProgram = program(RaffleDrumSpinGpuShaders.foilVertex, RaffleDrumSpinGpuShaders.foilFragment)
            foilTexture = texture("raffledrumspin/raffledrumspin_confetti-atlas.webp", true)
            foilLoaded = foilTexture != 0
        } else {
            drumProgram = program(RaffleDrumSpinGpuShaders.drumVertex, RaffleDrumSpinGpuShaders.drumFragment)
            ballProgram = program(RaffleDrumSpinGpuShaders.ballVertex, RaffleDrumSpinGpuShaders.ballFragment)
            envTexture = texture("raffledrumspin/raffledrumspin_env-studio.jpg", false)
            envLoaded = envTexture != 0
            val mesh = RaffleDrumSpinGeometry.build()
            vertexCount = mesh.size / 8
            val bytes = ByteBuffer.allocateDirect(mesh.size * 4).order(ByteOrder.nativeOrder())
            bytes.asFloatBuffer().put(mesh)
            val ids = IntArray(1); GL.glGenBuffers(1, ids, 0); meshBuffer = ids[0]
            GL.glBindBuffer(GL.GL_ARRAY_BUFFER, meshBuffer)
            GL.glBufferData(GL.GL_ARRAY_BUFFER, mesh.size * 4, bytes, GL.GL_STATIC_DRAW)
            GL.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
        }
    }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        screenWidth = width; screenHeight = height; GL.glViewport(0, 0, width, height)
        emptyFoil = false
    }
    override fun onDrawFrame(gl: GL10?) {
        val next = state.get()
        if (foil && emptyFoil && next != null && next.frame.burstAge < 0f) {
            frameChanged = false
            return
        }
        frameChanged = true
        GL.glDepthMask(true)
        GL.glClear(GL.GL_COLOR_BUFFER_BIT or GL.GL_DEPTH_BUFFER_BIT)
        val current = next ?: return
        val frame = current.frame
        val sx = screenWidth / RDS_REF_W
        val sy = screenHeight / RDS_REF_H
        val pixelRatio = context.resources.displayMetrics.density
        if (foil) {
            emptyFoil = frame.burstAge < 0f
            if (emptyFoil) return
            GL.glDisable(GL.GL_DEPTH_TEST); GL.glDepthMask(false)
            GL.glUseProgram(foilProgram)
            uniforms(foilProgram, current, sx, sy, pixelRatio)
            bindTexture(foilProgram, "confTex", foilTexture)
            GL.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, 6, 200)
            GL.glDepthMask(true)
            return
        }
        GL.glEnable(GL.GL_DEPTH_TEST)
        GL.glUseProgram(drumProgram); uniforms(drumProgram, current, sx, sy, pixelRatio)
        bindTexture(drumProgram, "envTex", envTexture)
        GL.glBindBuffer(GL.GL_ARRAY_BUFFER, meshBuffer)
        for (i in 0..3) { GL.glEnableVertexAttribArray(i); GL.glVertexAttribPointer(i, if (i < 2) 3 else 1, GL.GL_FLOAT, false, 32, if (i == 0) 0 else if (i == 1) 12 else if (i == 2) 24 else 28) }
        GL.glUniform1f(GL.glGetUniformLocation(drumProgram, "instanceOffset"), 0f)
        GL.glDrawArrays(GL.GL_TRIANGLES, 0, vertexCount)
        for (i in 0..3) GL.glDisableVertexAttribArray(i)
        GL.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
        GL.glUseProgram(ballProgram); uniforms(ballProgram, current, sx, sy, pixelRatio)
        repeat(3) { i ->
            val b=frame.balls.getOrNull(i)
            positions[i*4]=b?.x ?: 0f; positions[i*4+1]=b?.y ?: 0f
            positions[i*4+2]=b?.radius ?: 1f; positions[i*4+3]=b?.depth ?: 5f
            rotations[i*4]=b?.rx ?: 0f; rotations[i*4+1]=b?.ry ?: 0f; rotations[i*4+2]=b?.rz ?: 0f
            rotations[i*4+3]=b?.alpha ?: 0f; effects[i*4]=b?.glow ?: 0f
        }
        GL.glUniform4fv(GL.glGetUniformLocation(ballProgram,"ballPos"),3,positions,0)
        GL.glUniform4fv(GL.glGetUniformLocation(ballProgram,"ballRot"),3,rotations,0)
        GL.glUniform4fv(GL.glGetUniformLocation(ballProgram,"ballFx"),3,effects,0)
        GL.glDrawArraysInstanced(GL.GL_TRIANGLES,0,6,3)
        if (abs(frame.omega)>4f) {
            GL.glDepthMask(false); GL.glUseProgram(drumProgram)
            GL.glUniform1f(GL.glGetUniformLocation(drumProgram,"instanceOffset"),1f)
            GL.glBindBuffer(GL.GL_ARRAY_BUFFER,meshBuffer)
            for (i in 0..3) { GL.glEnableVertexAttribArray(i); GL.glVertexAttribPointer(i,if(i<2) 3 else 1,GL.GL_FLOAT,false,32,if(i==0) 0 else if(i==1) 12 else if(i==2) 24 else 28) }
            GL.glDrawArraysInstanced(GL.GL_TRIANGLES,0,vertexCount,5)
            for (i in 0..3) GL.glDisableVertexAttribArray(i)
            GL.glBindBuffer(GL.GL_ARRAY_BUFFER,0); GL.glDepthMask(true)
        }
    }
    private fun uniforms(program: Int, state: State, sx: Float, sy: Float, pr: Float) {
        val f=state.frame
        uniform(program,"viewport",screenWidth.toFloat(),screenHeight.toFloat(),pr,f.t)
        uniform(program,"drum",RdsLayout.drumX*sx,RdsLayout.drumY*sy,RdsLayout.drumR*6f*sx,f.theta)
        uniform(program,"cam",cos(.2f),sin(.2f),cos(-.27f),sin(-.27f))
        uniform(program,"misc",6f,f.speed,f.glow,f.burstAge)
        uniform(program,"burst",201f*sx,430f*sy,56f*sx,200f)
        uniform(program,"ghost",abs(f.omega)/60f/5f,if(abs(f.omega)>4f) 5f else 0f,if(envLoaded) 1f else 0f,if(foilLoaded) 1f else 0f)
    }
    private fun uniform(p:Int,name:String,x:Float,y:Float,z:Float,w:Float) = GL.glUniform4f(GL.glGetUniformLocation(p,name),x,y,z,w)
    private fun bindTexture(p:Int,name:String,id:Int) { GL.glActiveTexture(GL.GL_TEXTURE0); GL.glBindTexture(GL.GL_TEXTURE_2D,id); GL.glUniform1i(GL.glGetUniformLocation(p,name),0) }
    private fun texture(path:String, atlas:Boolean): Int {
        return try {
            // Android's bitmap cropper requires premultiplied pixels. GL uses the atlas' alpha
            // explicitly in the fragment shader, so uploading this representation is equivalent.
            val options=BitmapFactory.Options().apply { inPremultiplied=true }
            val bitmap=context.assets.open(path).use { BitmapFactory.decodeStream(it,null,options) } ?: return 0
            val ids=IntArray(1); GL.glGenTextures(1,ids,0); GL.glBindTexture(GL.GL_TEXTURE_2D,ids[0])
            GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MAG_FILTER,GL.GL_LINEAR)
            GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MIN_FILTER,if(atlas) GL.GL_LINEAR_MIPMAP_LINEAR else GL.GL_LINEAR)
            GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_S,if(atlas) GL.GL_CLAMP_TO_EDGE else GL.GL_REPEAT)
            GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_T,GL.GL_CLAMP_TO_EDGE)
            if(atlas) {
                val levels=listOf(Triple(0,0,1024),Triple(1024,0,512),Triple(1024,512,256),Triple(1280,512,128),Triple(1408,512,64))
                // WebGPU uploads the atlas with premultiplyAlpha=false. BitmapFactory stores the
                // decoded WebP premultiplied internally, but getPixels() returns straight ARGB;
                // upload those channels explicitly so the fragment shader does not multiply
                // alpha twice around each foil/sparkle edge.
                levels.forEachIndexed { level,(x,y,size) ->
                    val pixels = IntArray(size * size)
                    bitmap.getPixels(pixels, 0, size, x, y, size, size)
                    val rgba = ByteBuffer.allocateDirect(size * size * 4).order(ByteOrder.nativeOrder())
                    pixels.forEach { color ->
                        rgba.put(((color ushr 16) and 0xff).toByte())
                        rgba.put(((color ushr 8) and 0xff).toByte())
                        rgba.put((color and 0xff).toByte())
                        rgba.put(((color ushr 24) and 0xff).toByte())
                    }
                    rgba.flip()
                    GL.glTexImage2D(GL.GL_TEXTURE_2D, level, GL.GL_RGBA, size, size, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, rgba)
                }
                GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MAX_LEVEL,4)
            } else GLUtils.texImage2D(GL.GL_TEXTURE_2D,0,bitmap,0)
            bitmap.recycle(); ids[0]
        } catch (e: Exception) { Log.w("RaffleDrumSpin", "Texture load failed: $path", e); 0 }
    }
    private fun program(v:String,f:String): Int {
        val p=GL.glCreateProgram(); val vs=shader(GL.GL_VERTEX_SHADER,v); val fs=shader(GL.GL_FRAGMENT_SHADER,f)
        GL.glAttachShader(p,vs); GL.glAttachShader(p,fs); GL.glLinkProgram(p)
        val ok=IntArray(1); GL.glGetProgramiv(p,GL.GL_LINK_STATUS,ok,0)
        check(ok[0]!=0) { "GPU program link: ${GL.glGetProgramInfoLog(p)}" }
        GL.glDeleteShader(vs); GL.glDeleteShader(fs); return p
    }
    private fun shader(type:Int,source:String): Int {
        val s=GL.glCreateShader(type); GL.glShaderSource(s,source); GL.glCompileShader(s)
        val ok=IntArray(1); GL.glGetShaderiv(s,GL.GL_COMPILE_STATUS,ok,0)
        check(ok[0]!=0) { "GPU shader compile: ${GL.glGetShaderInfoLog(s)}" }; return s
    }
}
