// Murmuration Flock — the birds on OpenGL ES 3, in a TextureView with its own render thread
// (gpu/MfGpu.tsx).
//
// Each frame the block's ticker hands over one small array (Frame in MfMotion.kt). The render thread
// keeps only the newest; if it fell behind, the skipped frames' time steps are added to it, so the
// finger's spring still integrates the time that passed. A frame runs the step (transform feedback
// over every bird, ping-ponging two buffers) and the instanced draw. Where OpenGL ES 3 is missing or
// anything fails to build, it reports false and the block shows its drawn fallback.
package com.ngocdevv.nativecook.blocks.gestures.murmurationflock

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30 as GL
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.TextureView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

private const val EGL_OPENGL_ES3_BIT = 0x40
/** Bird in bytes: pos, head, disp, vel */
private const val BIRD_BYTES = 32
/** the most time one step may integrate, seconds, however far the render thread fell behind */
private const val MAX_STEP = 0.1f

internal class MfFlockView(
    context: Context,
    private val onAvailable: (Boolean) -> Unit,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private val thread = HandlerThread("MurmurationFlock").apply { start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())

    private val lock = Any()
    /** the newest frame from the ticker, and whether the render thread has taken it */
    private val inbox = FloatArray(Frame.LENGTH)
    private var inboxFresh = false
    private val posted = AtomicBoolean(false)
    private val latest = FloatArray(Frame.LENGTH)

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var renderer: MfRenderer? = null
    private var reported = false
    @Volatile private var released = false

    init {
        isOpaque = false
        isClickable = false
        isFocusable = false
        surfaceTextureListener = this
    }

    /** called on the main thread by the ticker */
    fun present(frame: FloatArray) {
        if (released) return
        synchronized(lock) {
            val skipped = if (inboxFresh) inbox[Frame.DT] else 0f
            frame.copyInto(inbox)
            inbox[Frame.DT] = min(MAX_STEP, frame[Frame.DT] + skipped)
            inboxFresh = true
        }
        if (posted.compareAndSet(false, true)) handler.post(draw)
    }

    private val draw = Runnable {
        posted.set(false)
        val fresh = synchronized(lock) {
            val was = inboxFresh
            if (was) inbox.copyInto(latest)
            inboxFresh = false
            was
        }
        val r = renderer ?: return@Runnable
        if (!fresh || released || surface == EGL14.EGL_NO_SURFACE) return@Runnable
        try {
            r.frame(latest, true)
            EGL14.eglSwapBuffers(display, surface)
        } catch (e: Exception) {
            Log.w("MurmurationFlock", "flock frame failed", e)
            report(false)
        }
    }

    private fun report(ok: Boolean) {
        if (reported) return
        reported = true
        main.post { if (!released) onAvailable(ok) }
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        handler.post {
            if (released) return@post
            try {
                startGl(texture)
                val r = MfRenderer(width, height)
                renderer = r
                // two frames (the newest the ticker sent, with no time step) before the loop may start
                synchronized(lock) { inbox.copyInto(latest) }
                r.frame(latest, false)
                EGL14.eglSwapBuffers(display, surface)
                r.frame(latest, false)
                EGL14.eglSwapBuffers(display, surface)
                report(true)
            } catch (e: Exception) {
                Log.w("MurmurationFlock", "OpenGL ES 3 flock unavailable, drawing the fallback", e)
                stopGl()
                report(false)
            }
        }
    }

    private fun startGl(texture: SurfaceTexture) {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY && EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0))
        val attributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT, EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0)
        eglContext = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        check(eglContext != EGL14.EGL_NO_CONTEXT)
        surface = EGL14.eglCreateWindowSurface(display, configs[0], texture, intArrayOf(EGL14.EGL_NONE), 0)
        check(surface != EGL14.EGL_NO_SURFACE)
        check(EGL14.eglMakeCurrent(display, surface, surface, eglContext))
    }

    private fun stopGl() {
        renderer = null
        if (display == EGL14.EGL_NO_DISPLAY) return
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
        // destroying the context frees its buffers, arrays and programs
        if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext)
        surface = EGL14.EGL_NO_SURFACE
        eglContext = EGL14.EGL_NO_CONTEXT
        display = EGL14.EGL_NO_DISPLAY
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        handler.post { renderer?.resize(width, height) }
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        // after release() the thread may be gone: then nobody else will free the texture
        val queued = handler.post {
            stopGl()
            texture.release()
        }
        if (!queued) texture.release()
        return false
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    fun release() {
        released = true
        main.removeCallbacksAndMessages(null)
        handler.post {
            stopGl()
            thread.quitSafely()
        }
    }
}

/** everything that needs the GL context; lives and dies on the render thread */
private class MfRenderer(private var width: Int, private var height: Int) {
    private val buffers = IntArray(2)
    private val stepArrays = IntArray(2)
    private val drawArrays = IntArray(2)
    private val step: Int = program(MF_STEP_VS, MF_STEP_FS, arrayOf("vPos", "vHead", "vDisp", "vVel"))
    private val bird: Int = program(MF_BIRD_VS, MF_BIRD_FS, null)
    private val stepSim = GL.glGetUniformLocation(step, "uSim")
    private val stepView = GL.glGetUniformLocation(step, "uView")
    private val stepLook = GL.glGetUniformLocation(step, "uLook")
    private val stepTouch = GL.glGetUniformLocation(step, "uTouch")
    private val birdSim = GL.glGetUniformLocation(bird, "uSim")
    private val birdView = GL.glGetUniformLocation(bird, "uView")
    /** which buffer holds the birds now */
    private var at = 0
    private var epoch = Float.NaN

    init {
        GL.glDisable(GL.GL_DEPTH_TEST)
        GL.glDisable(GL.GL_DITHER)
        // zeroed at creation: nobody has been pushed yet
        val zeros = ByteBuffer.allocateDirect(Flock.COUNT * BIRD_BYTES).order(ByteOrder.nativeOrder())
        GL.glGenBuffers(2, buffers, 0)
        for (b in buffers) {
            GL.glBindBuffer(GL.GL_ARRAY_BUFFER, b)
            GL.glBufferData(GL.GL_ARRAY_BUFFER, Flock.COUNT * BIRD_BYTES, zeros, GL.GL_DYNAMIC_COPY)
        }
        GL.glGenVertexArrays(2, stepArrays, 0)
        GL.glGenVertexArrays(2, drawArrays, 0)
        for (i in 0..1) {
            // the step reads disp and vel, one bird per vertex
            GL.glBindVertexArray(stepArrays[i])
            GL.glBindBuffer(GL.GL_ARRAY_BUFFER, buffers[i])
            GL.glEnableVertexAttribArray(0)
            GL.glVertexAttribPointer(0, 2, GL.GL_FLOAT, false, BIRD_BYTES, 16)
            GL.glEnableVertexAttribArray(1)
            GL.glVertexAttribPointer(1, 2, GL.GL_FLOAT, false, BIRD_BYTES, 24)
            // the draw reads pos and head, one bird per instance
            GL.glBindVertexArray(drawArrays[i])
            GL.glBindBuffer(GL.GL_ARRAY_BUFFER, buffers[i])
            GL.glEnableVertexAttribArray(0)
            GL.glVertexAttribPointer(0, 2, GL.GL_FLOAT, false, BIRD_BYTES, 0)
            GL.glVertexAttribDivisor(0, 1)
            GL.glEnableVertexAttribArray(1)
            GL.glVertexAttribPointer(1, 2, GL.GL_FLOAT, false, BIRD_BYTES, 8)
            GL.glVertexAttribDivisor(1, 1)
        }
        GL.glBindVertexArray(0)
        GL.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
        check(GL.glGetError() == GL.GL_NO_ERROR)
    }

    fun resize(w: Int, h: Int) {
        width = w
        height = h
    }

    /** one frame: the step (when `advance`), then the draw */
    fun frame(f: FloatArray, advance: Boolean) {
        GL.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0)
        GL.glViewport(0, 0, width, height)
        GL.glClearColor(0f, 0f, 0f, 0f)
        GL.glClear(GL.GL_COLOR_BUFFER_BIT)
        val w = f[Frame.WIDTH]
        val h = f[Frame.HEIGHT]
        if (w <= 0f || h <= 0f) return
        val clear = f[Frame.EPOCH] != epoch
        epoch = f[Frame.EPOCH]

        // the step: always run, with no time step for a still frame, so pos and head follow t
        GL.glUseProgram(step)
        GL.glUniform4f(stepSim, f[Frame.T], if (advance) f[Frame.DT] else 0f, if (clear) 1f else 0f, f[Frame.HOLD])
        GL.glUniform4f(stepView, w, h, f[Frame.CENTRE_X], f[Frame.CENTRE_Y])
        GL.glUniform4f(stepLook, f[Frame.UNIT], 0f, 0f, 0f)
        GL.glUniform4f(stepTouch, f[Frame.TOUCH_X], f[Frame.TOUCH_Y], f[Frame.TOUCH_ON], 0f)
        val target = 1 - at
        GL.glEnable(GL.GL_RASTERIZER_DISCARD)
        GL.glBindVertexArray(stepArrays[at])
        GL.glBindBufferBase(GL.GL_TRANSFORM_FEEDBACK_BUFFER, 0, buffers[target])
        GL.glBeginTransformFeedback(GL.GL_POINTS)
        GL.glDrawArrays(GL.GL_POINTS, 0, Flock.COUNT)
        GL.glEndTransformFeedback()
        GL.glBindBufferBase(GL.GL_TRANSFORM_FEEDBACK_BUFFER, 0, 0)
        GL.glDisable(GL.GL_RASTERIZER_DISCARD)
        at = target

        // the draw: premultiplied darts over a clear surface
        GL.glEnable(GL.GL_BLEND)
        GL.glBlendFuncSeparate(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA, GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA)
        GL.glUseProgram(bird)
        GL.glUniform4f(birdSim, f[Frame.T], 0f, 0f, 0f)
        GL.glUniform4f(birdView, w, h, f[Frame.CENTRE_X], f[Frame.CENTRE_Y])
        GL.glBindVertexArray(drawArrays[at])
        GL.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, 6, Flock.COUNT)
        GL.glBindVertexArray(0)
        GL.glDisable(GL.GL_BLEND)
    }

    private fun program(vertex: String, fragment: String, varyings: Array<String>?): Int {
        val id = GL.glCreateProgram()
        GL.glAttachShader(id, shader(GL.GL_VERTEX_SHADER, vertex))
        GL.glAttachShader(id, shader(GL.GL_FRAGMENT_SHADER, fragment))
        if (varyings != null) GL.glTransformFeedbackVaryings(id, varyings, GL.GL_INTERLEAVED_ATTRIBS)
        GL.glLinkProgram(id)
        val ok = IntArray(1)
        GL.glGetProgramiv(id, GL.GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { GL.glGetProgramInfoLog(id) }
        return id
    }

    private fun shader(type: Int, source: String): Int {
        val id = GL.glCreateShader(type)
        GL.glShaderSource(id, source)
        GL.glCompileShader(id)
        val ok = IntArray(1)
        GL.glGetShaderiv(id, GL.GL_COMPILE_STATUS, ok, 0)
        check(ok[0] != 0) { GL.glGetShaderInfoLog(id) }
        return id
    }
}
