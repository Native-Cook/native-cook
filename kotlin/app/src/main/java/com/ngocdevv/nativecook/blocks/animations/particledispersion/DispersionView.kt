package com.ngocdevv.nativecook.blocks.animations.particledispersion

import com.ngocdevv.nativecook.R

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.view.Choreographer
import java.util.concurrent.atomic.AtomicLong
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** A native vsync clock requests GPU frames only while the effect is playing. */
internal class DispersionView(context: Context) : GLSurfaceView(context), GLSurfaceView.Renderer {
  var onComplete: (() -> Unit)? = null
  var onReady: (() -> Unit)? = null
  var onError: ((Throwable) -> Unit)? = null
  private val generation = AtomicLong(0)
  private var renderer: ParticleRenderer? = null
  private var uploaded: EffectScene? = null
  @Volatile private var scene: EffectScene? = null
  @Volatile private var snapshot = Snapshot(0f, 0f, false, 0)
  private var startNanos = 0L
  private var elapsedNanos = 0L
  private var playing = false
  private var suspended = false
  private var completionSent = -1L
  private val choreographer = Choreographer.getInstance()
  private val ticker = object : Choreographer.FrameCallback {
    override fun doFrame(frameTimeNanos: Long) {
      if (!playing || suspended) return
      if (startNanos == 0L) startNanos = frameTimeNanos - elapsedNanos
      elapsedNanos = frameTimeNanos - startNanos
      val duration = if (snapshot.reduced) 0.260f else 3.9f
      val seconds = (elapsedNanos / 1_000_000_000.0).toFloat().coerceAtMost(duration)
      snapshot = Snapshot(if (snapshot.reduced) 0f else seconds, seconds / duration,
        snapshot.reduced, generation.get())
      requestRender()
      if (seconds < duration) choreographer.postFrameCallback(this) else playing = false
    }
  }

  private data class Snapshot(val time: Float, val progress: Float, val reduced: Boolean, val run: Long)

  init {
    id = R.id.particledispersion_dispersion_canvas
    setEGLContextClientVersion(3)
    setEGLConfigChooser(8, 8, 8, 8, 0, 0)
    preserveEGLContextOnPause = true
    setRenderer(this)
    renderMode = RENDERMODE_WHEN_DIRTY
    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
  }

  fun show(value: EffectScene) {
    stop()
    scene = value
    snapshot = Snapshot(0f, 0f, false, generation.get())
    requestRender()
  }

  fun play(reduced: Boolean) {
    stop()
    playing = true
    startNanos = 0
    elapsedNanos = 0
    snapshot = Snapshot(0f, 0f, reduced, generation.get())
    choreographer.postFrameCallback(ticker)
  }

  fun stop() {
    generation.incrementAndGet()
    choreographer.removeFrameCallback(ticker)
    playing = false
    startNanos = 0
    elapsedNanos = 0
    suspended = false
  }

  fun suspendPlayback() {
    if (playing) {
      choreographer.removeFrameCallback(ticker)
      suspended = true
      startNanos = 0
    }
  }

  fun resumePlayback() {
    if (playing && suspended) {
      suspended = false
      startNanos = 0
      choreographer.postFrameCallback(ticker)
    }
  }

  override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
    uploaded = null
    try { renderer = ParticleRenderer() }
    catch (error: Throwable) { fail(error, generation.get()) }
  }

  override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) { requestRender() }

  override fun onDrawFrame(gl: GL10?) {
    val current = scene
    val state = snapshot
    try {
      val gpu = renderer ?: return
      if (current == null) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        return
      }
      if (uploaded !== current) {
        val frame = requireNotNull(BitmapFactory.decodeFile(current.frame.path)) { "The photo could not be decoded." }
        val mask = try { requireNotNull(BitmapFactory.decodeFile(current.mask.path)) { "The subject mask could not be decoded." } }
        catch (error: Throwable) { frame.recycle(); throw error }
        try { gpu.uploadTextures(frame, mask); uploaded = current }
        finally { frame.recycle(); mask.recycle() }
        post { if (scene === current) onReady?.invoke() }
      }
      val logicalWidth = width / resources.displayMetrics.density
      val logicalHeight = height / resources.displayMetrics.density
      if (width <= 0 || height <= 0) return
      gpu.render(ParticleRenderState(0,
        ParticleFrameIdentity(current.frame.path, current.mask.path), logicalWidth, logicalHeight,
        state.time, state.progress, 0.417f, 2.6f, 1f.toRawBits(), state.reduced,
        floatArrayOf(125f / 255, 97f / 255, 75f / 255, 1f),
        current.displayBounds(logicalWidth, logicalHeight), 0f), width, height)
      if (state.progress >= 1f && completionSent != state.run) {
        completionSent = state.run
        post { if (generation.get() == state.run) onComplete?.invoke() }
      }
    } catch (error: Throwable) { fail(error, state.run) }
  }

  private fun fail(error: Throwable, run: Long) {
    post {
      if (generation.get() == run) { stop(); onError?.invoke(error) }
    }
  }
}
