// Ripple Pond Photo — the water on OpenGL ES 3, in a TextureView with its own render thread.
//
// Each frame the block's ticker hands over how many simulation steps to run, the damping and up to
// four drops (Frame in RppMotion.kt). They are queued, so a frame the render thread is late for still
// presses its drops in; the steps run on two ping-ponged RGBA16F height textures and the photo is
// drawn through the result. Where a float texture cannot be rendered to, the same view draws the
// analytic rings instead (RINGS_FS). If OpenGL ES 3 itself is missing it reports false and the still
// photo under it stays.
package com.ngocdevv.nativecook.blocks.gestures.ripplepondphoto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30 as GL
import android.opengl.GLUtils
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.TextureView
import java.util.concurrent.atomic.AtomicBoolean

/** frames the render thread may fall behind by before the oldest are dropped */
private const val MAX_QUEUED = 16
/** most steps one drain may run, so a late render thread catches up without a spike */
private const val MAX_DRAIN_STEPS = SIM_MAX_STEPS * 2
private const val EGL_OPENGL_ES3_BIT = 0x40

internal class RppWaterView(
    context: Context,
    private val photo: Bitmap,
    private val rows: Int,
    private val onAvailable: (Boolean) -> Unit,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private val thread = HandlerThread("RipplePondPhoto").apply { start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private val pixelRatio = resources.displayMetrics.density

    private val lock = Any()
    private val queue = ArrayDeque<FloatArray>()
    private val free = ArrayDeque<FloatArray>()
    private val posted = AtomicBoolean(false)
    private val latest = FloatArray(Frame.LENGTH)

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var renderer: RppRenderer? = null
    private var presented = 0
    private var reported = false
    @Volatile private var released = false

    init {
        isOpaque = false
        isClickable = false
        isFocusable = false
        surfaceTextureListener = this
    }

    /** called on the main thread by the ticker, once per frame */
    fun present(frame: FloatArray) {
        if (released) return
        synchronized(lock) {
            val slot = free.removeLastOrNull() ?: FloatArray(Frame.LENGTH)
            frame.copyInto(slot)
            queue.addLast(slot)
            while (queue.size > MAX_QUEUED) free.addLast(queue.removeFirst())
        }
        if (posted.compareAndSet(false, true)) handler.post(draw)
    }

    private val draw = Runnable {
        posted.set(false)
        val r = renderer
        var budget = MAX_DRAIN_STEPS
        var any = false
        while (true) {
            val next = synchronized(lock) { queue.removeFirstOrNull() } ?: break
            if (r != null) budget -= r.advance(next, budget)
            next.copyInto(latest)
            any = true
            synchronized(lock) { free.addLast(next) }
        }
        if (r == null || !any || released || surface == EGL14.EGL_NO_SURFACE) return@Runnable
        r.render(latest)
        EGL14.eglSwapBuffers(display, surface)
        presented += 1
        if (presented == 2) report(true)
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
                renderer = RppRenderer(photo, rows, width, height, pixelRatio)
            } catch (_: Exception) {
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
        // destroying the context frees its textures, framebuffers and programs
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
private class RppRenderer(photo: Bitmap, private val rows: Int, private var width: Int, private var height: Int, private val pixelRatio: Float) {
    private val photoTex: Int
    private val scaleX: Float
    private val scaleY: Float
    private val vao: Int

    /** null where a float texture cannot be rendered to: the analytic rings are drawn instead */
    private val sim: Sim?
    private val rings: Int
    private val noDrops = FloatArray(16)
    private var epoch = 0f

    private class Sim(val step: Int, val view: Int, val textures: IntArray, val buffers: IntArray) {
        /** which texture holds the water now */
        var at = 0
    }

    init {
        GL.glDisable(GL.GL_BLEND)
        GL.glDisable(GL.GL_DITHER)
        GL.glDisable(GL.GL_DEPTH_TEST)
        val array = IntArray(1)
        GL.glGenVertexArrays(1, array, 0)
        vao = array[0]
        GL.glBindVertexArray(vao)

        photoTex = newTexture()
        GLUtils.texImage2D(GL.GL_TEXTURE_2D, 0, photo, 0)
        // the photo covers the screen: the axis that overflows is cropped evenly
        val screen = width.toFloat() / height
        val picture = photo.width.toFloat() / photo.height
        scaleX = if (screen > picture) 1f else screen / picture
        scaleY = if (screen > picture) picture / screen else 1f

        sim = runCatching { buildSim() }.getOrNull()
        rings = if (sim == null) program(FULL_VS, RINGS_FS) else 0
    }

    private fun newTexture(): Int {
        val id = IntArray(1)
        GL.glGenTextures(1, id, 0)
        GL.glBindTexture(GL.GL_TEXTURE_2D, id[0])
        GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR)
        GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR)
        GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE)
        GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE)
        return id[0]
    }

    private fun buildSim(): Sim {
        val extensions = GL.glGetString(GL.GL_EXTENSIONS).orEmpty()
        check("GL_EXT_color_buffer_float" in extensions || "GL_EXT_color_buffer_half_float" in extensions)
        val textures = IntArray(2)
        val buffers = IntArray(2)
        GL.glGenFramebuffers(2, buffers, 0)
        for (i in 0..1) {
            textures[i] = newTexture()
            GL.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA16F, SIM_GRID_W, rows, 0, GL.GL_RGBA, GL.GL_HALF_FLOAT, null)
            GL.glBindFramebuffer(GL.GL_FRAMEBUFFER, buffers[i])
            GL.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, textures[i], 0)
            check(GL.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER) == GL.GL_FRAMEBUFFER_COMPLETE)
        }
        GL.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0)
        val built = Sim(program(FULL_VS, STEP_FS), program(FULL_VS, VIEW_FS), textures, buffers)
        clear(built)
        return built
    }

    private fun clear(s: Sim) {
        GL.glClearColor(0f, 0f, 0f, 1f)
        for (buffer in s.buffers) {
            GL.glBindFramebuffer(GL.GL_FRAMEBUFFER, buffer)
            GL.glClear(GL.GL_COLOR_BUFFER_BIT)
        }
        GL.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0)
    }

    fun resize(w: Int, h: Int) {
        width = w
        height = h
    }

    /** one queued frame: its clear, then its steps with the drops in the first. Returns the steps run. */
    fun advance(f: FloatArray, budget: Int): Int {
        val s = sim ?: return 0
        if (f[Frame.EPOCH] != epoch) {
            epoch = f[Frame.EPOCH]
            clear(s)
        }
        val wanted = f[Frame.STEPS].toInt()
        if (wanted <= 0) return 0
        var hasDrops = false
        for (i in 0 until 4) if (f[Frame.DROPS + i * 4 + 3] != 0f) hasDrops = true
        // over budget a frame still presses its drops in, with one step
        val steps = if (budget >= wanted) wanted else if (hasDrops) 1 else maxOf(0, budget)
        if (steps == 0) return 0

        GL.glUseProgram(s.step)
        GL.glViewport(0, 0, SIM_GRID_W, rows)
        GL.glUniform4f(GL.glGetUniformLocation(s.step, "uCfg"), f[Frame.C2], f[Frame.DAMP], SIM_GRID_W.toFloat(), rows.toFloat())
        GL.glUniform1i(GL.glGetUniformLocation(s.step, "uField"), 0)
        val dropsAt = GL.glGetUniformLocation(s.step, "uDrops")
        GL.glActiveTexture(GL.GL_TEXTURE0)
        for (n in 0 until steps) {
            if (n == 0) GL.glUniform4fv(dropsAt, 4, f, Frame.DROPS) else if (n == 1) GL.glUniform4fv(dropsAt, 4, noDrops, 0)
            val target = 1 - s.at
            GL.glBindFramebuffer(GL.GL_FRAMEBUFFER, s.buffers[target])
            GL.glBindTexture(GL.GL_TEXTURE_2D, s.textures[s.at])
            GL.glDrawArrays(GL.GL_TRIANGLES, 0, 3)
            s.at = target
        }
        GL.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0)
        return steps
    }

    fun render(f: FloatArray) {
        GL.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0)
        GL.glViewport(0, 0, width, height)
        val s = sim
        val p = s?.view ?: rings
        GL.glUseProgram(p)
        GL.glUniform4f(GL.glGetUniformLocation(p, "uCanvas"), width.toFloat(), height.toFloat(), pixelRatio, 0f)
        GL.glUniform4f(GL.glGetUniformLocation(p, "uGrid"), SIM_GRID_W.toFloat(), rows.toFloat(), scaleX, scaleY)
        GL.glUniform1i(GL.glGetUniformLocation(p, "uPhoto"), 1)
        GL.glActiveTexture(GL.GL_TEXTURE1)
        GL.glBindTexture(GL.GL_TEXTURE_2D, photoTex)
        if (s != null) {
            GL.glUniform4f(GL.glGetUniformLocation(p, "uBend"), LOOK_REFRACT, LOOK_REFRACT_MAX, LOOK_CHROMA, LOOK_NORMAL)
            GL.glUniform4f(GL.glGetUniformLocation(p, "uLight"), LOOK_SPEC_POWER, LOOK_SPEC, LOOK_CAUSTIC, LOOK_SHADE)
            GL.glUniform1i(GL.glGetUniformLocation(p, "uField"), 0)
            GL.glActiveTexture(GL.GL_TEXTURE0)
            GL.glBindTexture(GL.GL_TEXTURE_2D, s.textures[s.at])
        } else {
            GL.glUniform2f(GL.glGetUniformLocation(p, "uClock"), f[Frame.CLOCK], f[Frame.LEVEL])
            GL.glUniform4fv(GL.glGetUniformLocation(p, "uRings"), RING_COUNT, f, Frame.RINGS)
        }
        GL.glDrawArrays(GL.GL_TRIANGLES, 0, 3)
    }

    private fun program(vertex: String, fragment: String): Int {
        val id = GL.glCreateProgram()
        GL.glAttachShader(id, shader(GL.GL_VERTEX_SHADER, vertex))
        GL.glAttachShader(id, shader(GL.GL_FRAGMENT_SHADER, fragment))
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
