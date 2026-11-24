// Coral Growth Streak — the reef on OpenGL ES 3, in a TextureView with its own render thread.
//
// Each frame the block's ticker hands over how many simulation steps to run, the open radius, the look
// and up to eight seeds (Frame in CgsMotion.kt). Frames are queued and never dropped while they carry
// steps, because a lost frame would lose a day's seeds: the ticker asks `canStep` first and, while the
// render thread is behind, sends frames without steps (those replace each other) and catches up later,
// counted from the loop time. Where OpenGL ES 3 or a renderable float texture is missing it reports
// false and the block shows its drawn fallback.
package com.ngocdevv.nativecook.blocks.progress.coralgrowthstreak

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
import android.view.TextureView
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** frames with steps the render thread may have waiting before the ticker holds its steps back */
private const val MAX_PENDING = 3
private const val EGL_OPENGL_ES3_BIT = 0x40

internal class CgsReefView(
    context: Context,
    private val onAvailable: (Boolean) -> Unit,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private val thread = HandlerThread("CoralGrowthStreak").apply { start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private val pixelRatio = resources.displayMetrics.density

    private val lock = Any()
    private val queue = ArrayDeque<FloatArray>()
    private val free = ArrayDeque<FloatArray>()
    private val posted = AtomicBoolean(false)
    private val pending = AtomicInteger(0)
    private val latest = FloatArray(Frame.LENGTH)

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var renderer: CgsRenderer? = null
    private var presented = 0
    private var reported = false
    @Volatile private var released = false

    /** false while the render thread is behind: the ticker then holds its steps back */
    val canStep: Boolean get() = pending.get() < MAX_PENDING

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
            val last = queue.lastOrNull()
            if (last != null && frame[Frame.STEPS] == 0f && last[Frame.STEPS] == 0f && last[Frame.EPOCH] == frame[Frame.EPOCH]) {
                frame.copyInto(last)
            } else {
                val slot = free.removeLastOrNull() ?: FloatArray(Frame.LENGTH)
                frame.copyInto(slot)
                queue.addLast(slot)
                if (frame[Frame.STEPS] > 0f) pending.incrementAndGet()
            }
        }
        if (posted.compareAndSet(false, true)) handler.post(draw)
    }

    private val draw = Runnable {
        posted.set(false)
        val r = renderer
        var any = false
        while (true) {
            val next = synchronized(lock) { queue.removeFirstOrNull() } ?: break
            r?.advance(next)
            if (next[Frame.STEPS] > 0f) pending.decrementAndGet()
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
                renderer = CgsRenderer(width, height, pixelRatio)
                // the first frames are shown before the loop may start, which waits for this view
                val idle = FloatArray(Frame.LENGTH)
                renderer?.render(idle)
                EGL14.eglSwapBuffers(display, surface)
                renderer?.render(idle)
                EGL14.eglSwapBuffers(display, surface)
                presented = 2
                report(true)
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
private class CgsRenderer(private var width: Int, private var height: Int, private val pixelRatio: Float) {
    private val textures = IntArray(2)
    private val buffers = IntArray(2)
    private val step: Int
    private val view: Int
    private val stepCfg: Int
    private val stepWall: Int
    private val stepSeeds: Int
    private val viewCanvas: Int
    private val viewAnim: Int
    private val noSeeds = FloatArray(SIM_SEED_SLOTS * 4)
    /** which texture holds the reef now */
    private var at = 0
    private var epoch = 0f

    init {
        GL.glDisable(GL.GL_BLEND)
        GL.glDisable(GL.GL_DITHER)
        GL.glDisable(GL.GL_DEPTH_TEST)
        val array = IntArray(1)
        GL.glGenVertexArrays(1, array, 0)
        GL.glBindVertexArray(array[0])

        val extensions = GL.glGetString(GL.GL_EXTENSIONS).orEmpty()
        check("GL_EXT_color_buffer_float" in extensions || "GL_EXT_color_buffer_half_float" in extensions)
        GL.glGenTextures(2, textures, 0)
        GL.glGenFramebuffers(2, buffers, 0)
        for (i in 0..1) {
            GL.glBindTexture(GL.GL_TEXTURE_2D, textures[i])
            GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR)
            GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR)
            GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE)
            GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE)
            GL.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA16F, SIM_GRID, SIM_GRID, 0, GL.GL_RGBA, GL.GL_HALF_FLOAT, null)
            GL.glBindFramebuffer(GL.GL_FRAMEBUFFER, buffers[i])
            GL.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, textures[i], 0)
            check(GL.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER) == GL.GL_FRAMEBUFFER_COMPLETE)
        }
        GL.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0)
        step = program(CGS_FULL_VS, CGS_STEP_FS)
        view = program(CGS_FULL_VS, CGS_VIEW_FS)
        stepCfg = GL.glGetUniformLocation(step, "uCfg")
        stepWall = GL.glGetUniformLocation(step, "uWall")
        stepSeeds = GL.glGetUniformLocation(step, "uSeeds")
        viewCanvas = GL.glGetUniformLocation(view, "uCanvas")
        viewAnim = GL.glGetUniformLocation(view, "uAnim")
        GL.glUseProgram(step)
        GL.glUniform1i(GL.glGetUniformLocation(step, "uField"), 0)
        GL.glUseProgram(view)
        GL.glUniform1i(GL.glGetUniformLocation(view, "uField"), 0)
        clear()
    }

    /** an empty reef: both textures u = 1, v = 0 */
    private fun clear() {
        GL.glClearColor(1f, 0f, 0f, 1f)
        for (buffer in buffers) {
            GL.glBindFramebuffer(GL.GL_FRAMEBUFFER, buffer)
            GL.glClear(GL.GL_COLOR_BUFFER_BIT)
        }
        GL.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0)
    }

    fun resize(w: Int, h: Int) {
        width = w
        height = h
    }

    /** one queued frame: its clear, then its steps with the seeds in the first */
    fun advance(f: FloatArray) {
        if (f[Frame.EPOCH] != epoch) {
            epoch = f[Frame.EPOCH]
            clear()
        }
        val steps = f[Frame.STEPS].toInt()
        if (steps <= 0) return
        GL.glUseProgram(step)
        GL.glViewport(0, 0, SIM_GRID, SIM_GRID)
        GL.glUniform4f(stepCfg, SIM_FEED, SIM_KILL, SIM_GRID.toFloat(), SIM_SEED_RADIUS)
        GL.glUniform4f(stepWall, f[Frame.RADIUS], SIM_WALL_KILL, SIM_WALL_WIDTH, 0f)
        GL.glActiveTexture(GL.GL_TEXTURE0)
        for (n in 0 until steps) {
            // the frame's seeds go in with its first step only
            if (n == 0) GL.glUniform4fv(stepSeeds, SIM_SEED_SLOTS, f, Frame.SEEDS) else if (n == 1) GL.glUniform4fv(stepSeeds, SIM_SEED_SLOTS, noSeeds, 0)
            val target = 1 - at
            GL.glBindFramebuffer(GL.GL_FRAMEBUFFER, buffers[target])
            GL.glBindTexture(GL.GL_TEXTURE_2D, textures[at])
            GL.glDrawArrays(GL.GL_TRIANGLES, 0, 3)
            at = target
        }
        GL.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0)
    }

    fun render(f: FloatArray) {
        GL.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0)
        GL.glViewport(0, 0, width, height)
        GL.glUseProgram(view)
        GL.glUniform4f(viewCanvas, width.toFloat(), height.toFloat(), SIM_GRID.toFloat(), pixelRatio)
        GL.glUniform4f(viewAnim, f[Frame.LEVEL], f[Frame.BLOOM], f[Frame.PHASE], 0f)
        GL.glActiveTexture(GL.GL_TEXTURE0)
        GL.glBindTexture(GL.GL_TEXTURE_2D, textures[at])
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
