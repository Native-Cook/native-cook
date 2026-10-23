package expo.modules.subjectsegmentation

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30

internal interface EglRenderThreadListener {
  fun onFramePresented(
    thread: EglRenderThread,
    generation: Long,
    identity: ParticleFrameIdentity
  )

  fun onRenderError(
    thread: EglRenderThread,
    generation: Long,
    identity: ParticleFrameIdentity,
    message: String
  )

  fun onRenderThreadStopped(thread: EglRenderThread)
}

/**
 * One EGL context bound to one TextureView SurfaceTexture. State submissions
 * overwrite one pending slot, so high-frequency animated props cannot build a queue.
 */
internal class EglRenderThread(
  private val windowTexture: SurfaceTexture,
  private val listener: EglRenderThreadListener
) : Thread("SubjectSegmentation.EGL"), AutoCloseable {
  companion object {
    private const val EGL_OPENGL_ES3_BIT_KHR = 0x0040
  }

  private val commandLock = Object()
  private var shouldRun = true
  private var acceptingCommands = true
  private var renderRequested = false
  private var latestState: ParticleRenderState? = null
  private var pendingBitmaps: ParticleBitmapPair? = null
  private var releaseWindowTextureOnExit = false
  private var windowTextureReleased = false
  private var terminated = false

  @Volatile
  var isAcceptingCommands: Boolean = true
    private set

  fun submitState(state: ParticleRenderState): Boolean {
    synchronized(commandLock) {
      if (!acceptingCommands) {
        return false
      }
      latestState = state
      renderRequested = true
      commandLock.notifyAll()
      return true
    }
  }

  fun submitBitmaps(bitmaps: ParticleBitmapPair): Boolean {
    var replaced: ParticleBitmapPair? = null
    synchronized(commandLock) {
      if (!acceptingCommands) {
        bitmaps.recycle()
        return false
      }
      replaced = pendingBitmaps
      pendingBitmaps = bitmaps
      renderRequested = true
      commandLock.notifyAll()
    }
    replaced?.recycle()
    return true
  }

  /** The SurfaceTexture is released only when TextureView transferred ownership to us. */
  fun shutdown(releaseWindowTexture: Boolean) {
    var pending: ParticleBitmapPair? = null
    var releaseImmediately = false
    synchronized(commandLock) {
      releaseWindowTextureOnExit = releaseWindowTextureOnExit || releaseWindowTexture
      if (acceptingCommands) {
        acceptingCommands = false
        isAcceptingCommands = false
        shouldRun = false
        pending = pendingBitmaps
        pendingBitmaps = null
        commandLock.notifyAll()
      }
      if (terminated && releaseWindowTextureOnExit && !windowTextureReleased) {
        windowTextureReleased = true
        releaseImmediately = true
      }
    }
    pending?.recycle()
    if (releaseImmediately) {
      releaseWindowTexture()
    }
    interrupt()
  }

  override fun close() {
    shutdown(releaseWindowTexture = false)
  }

  override fun run() {
    var display = EGL14.EGL_NO_DISPLAY
    var context = EGL14.EGL_NO_CONTEXT
    var surface = EGL14.EGL_NO_SURFACE
    var renderer: ParticleRenderer? = null
    try {
      val environment = createEnvironment()
      display = environment.display
      context = environment.context
      surface = environment.surface
      renderer = ParticleRenderer()
      renderLoop(renderer, display, surface)
    } catch (error: Throwable) {
      if (isStillRunning()) {
        currentState()?.let { state ->
          listener.onRenderError(
            this,
            state.generation,
            state.identity,
            error.message ?: "The GPU could not initialize the particle renderer."
          )
        }
      }
    } finally {
      recyclePendingBitmaps()
      if (display != EGL14.EGL_NO_DISPLAY && context != EGL14.EGL_NO_CONTEXT) {
        try {
          renderer?.release()
        } catch (_: Throwable) {
          // Context teardown below releases any resources a failed driver retained.
        }
      }
      destroyEnvironment(display, context, surface)

      var releaseTexture = false
      synchronized(commandLock) {
        acceptingCommands = false
        isAcceptingCommands = false
        shouldRun = false
        terminated = true
        if (releaseWindowTextureOnExit && !windowTextureReleased) {
          windowTextureReleased = true
          releaseTexture = true
        }
      }
      if (releaseTexture) {
        releaseWindowTexture()
      }
      listener.onRenderThreadStopped(this)
    }
  }

  private fun renderLoop(
    renderer: ParticleRenderer,
    display: EGLDisplay,
    surface: EGLSurface
  ) {
    var loadedGeneration = Long.MIN_VALUE
    var loadedIdentity: ParticleFrameIdentity? = null
    var failedGeneration = Long.MIN_VALUE

    while (true) {
      val command = nextCommand() ?: break
      val state = command.state
      if (state == null) {
        command.bitmaps?.recycle()
        continue
      }

      if (loadedGeneration != state.generation || loadedIdentity != state.identity) {
        renderer.clearTextures()
        loadedGeneration = Long.MIN_VALUE
        loadedIdentity = null
        failedGeneration = Long.MIN_VALUE
      }

      val bitmaps = command.bitmaps
      if (bitmaps != null) {
        if (bitmaps.generation != state.generation || bitmaps.identity != state.identity) {
          bitmaps.recycle()
        } else {
          try {
            renderer.uploadTextures(bitmaps.frame, bitmaps.mask)
            loadedGeneration = bitmaps.generation
            loadedIdentity = bitmaps.identity
          } catch (error: Throwable) {
            failedGeneration = state.generation
            renderer.clearTextures()
            listener.onRenderError(
              this,
              state.generation,
              state.identity,
              error.message ?: "The GPU could not load the particle textures."
            )
          } finally {
            bitmaps.recycle()
          }
        }
      }

      if (
        failedGeneration == state.generation ||
        loadedGeneration != state.generation ||
        loadedIdentity != state.identity
      ) {
        continue
      }

      try {
        val dimensions = querySurfaceDimensions(display, surface)
        if (dimensions.first <= 0 || dimensions.second <= 0) {
          continue
        }
        renderer.render(state, dimensions.first, dimensions.second)
        if (!EGL14.eglSwapBuffers(display, surface)) {
          throw ParticleRendererException(
            "The GPU could not present the particle frame (${eglError()})."
          )
        }
        listener.onFramePresented(this, state.generation, state.identity)
      } catch (error: Throwable) {
        failedGeneration = state.generation
        listener.onRenderError(
          this,
          state.generation,
          state.identity,
          error.message ?: "The GPU could not render the particle frame."
        )
      }
    }
  }

  private fun nextCommand(): RenderCommand? {
    synchronized(commandLock) {
      while (shouldRun && !renderRequested && pendingBitmaps == null) {
        try {
          commandLock.wait()
        } catch (_: InterruptedException) {
          if (!shouldRun) {
            return null
          }
        }
      }
      if (!shouldRun) {
        return null
      }
      val command = RenderCommand(latestState, pendingBitmaps)
      pendingBitmaps = null
      renderRequested = false
      return command
    }
  }

  private fun createEnvironment(): EglEnvironment {
    val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    if (display == EGL14.EGL_NO_DISPLAY) {
      throw ParticleRendererException("OpenGL ES is unavailable on this device (${eglError()}).")
    }

    val versions = IntArray(2)
    if (!EGL14.eglInitialize(display, versions, 0, versions, 1)) {
      throw ParticleRendererException("OpenGL ES could not initialize (${eglError()}).")
    }
    if (!EGL14.eglBindAPI(EGL14.EGL_OPENGL_ES_API)) {
      EGL14.eglTerminate(display)
      throw ParticleRendererException("OpenGL ES could not bind its rendering API (${eglError()}).")
    }

    val config = chooseConfig(display)
    val contextAttributes = intArrayOf(
      EGL14.EGL_CONTEXT_CLIENT_VERSION,
      3,
      EGL14.EGL_NONE
    )
    val context = EGL14.eglCreateContext(
      display,
      config,
      EGL14.EGL_NO_CONTEXT,
      contextAttributes,
      0
    )
    if (context == EGL14.EGL_NO_CONTEXT) {
      EGL14.eglTerminate(display)
      throw ParticleRendererException("This device could not create a GLES 3 context (${eglError()}).")
    }

    // The Apple path samples and blends non-sRGB textures into bgra8Unorm, then
    // tags the layer as sRGB. An EGL sRGB framebuffer would encode our already
    // encoded shader values a second time on OpenGL ES, so retain EGL's linear
    // default attachment and let Android composite the TextureView normally.
    val surface = EGL14.eglCreateWindowSurface(
      display,
      config,
      windowTexture,
      intArrayOf(EGL14.EGL_NONE),
      0
    )
    if (surface == EGL14.EGL_NO_SURFACE) {
      EGL14.eglDestroyContext(display, context)
      EGL14.eglTerminate(display)
      throw ParticleRendererException("The GPU could not create a particle render surface (${eglError()}).")
    }

    if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
      EGL14.eglDestroySurface(display, surface)
      EGL14.eglDestroyContext(display, context)
      EGL14.eglTerminate(display)
      throw ParticleRendererException("The GPU could not activate the particle render surface (${eglError()}).")
    }
    EGL14.eglSwapInterval(display, 0)

    val version = GLES30.glGetString(GLES30.GL_VERSION).orEmpty()
    if (!version.contains("OpenGL ES 3")) {
      destroyEnvironment(display, context, surface)
      throw ParticleRendererException("Particle rendering requires OpenGL ES 3.0.")
    }
    return EglEnvironment(display, context, surface)
  }

  private fun chooseConfig(display: EGLDisplay): EGLConfig {
    val attributes = intArrayOf(
      EGL14.EGL_SURFACE_TYPE,
      EGL14.EGL_WINDOW_BIT,
      EGL14.EGL_RENDERABLE_TYPE,
      EGL_OPENGL_ES3_BIT_KHR,
      EGL14.EGL_RED_SIZE,
      8,
      EGL14.EGL_GREEN_SIZE,
      8,
      EGL14.EGL_BLUE_SIZE,
      8,
      EGL14.EGL_ALPHA_SIZE,
      8,
      EGL14.EGL_DEPTH_SIZE,
      0,
      EGL14.EGL_STENCIL_SIZE,
      0,
      EGL14.EGL_NONE
    )
    val configs = arrayOfNulls<EGLConfig>(1)
    val count = IntArray(1)
    if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0)) {
      throw ParticleRendererException("The GPU could not choose a GLES 3 configuration (${eglError()}).")
    }
    return configs[0]
      ?: throw ParticleRendererException("This device has no compatible GLES 3 configuration.")
  }

  private fun querySurfaceDimensions(display: EGLDisplay, surface: EGLSurface): Pair<Int, Int> {
    val width = IntArray(1)
    val height = IntArray(1)
    if (
      !EGL14.eglQuerySurface(display, surface, EGL14.EGL_WIDTH, width, 0) ||
      !EGL14.eglQuerySurface(display, surface, EGL14.EGL_HEIGHT, height, 0)
    ) {
      throw ParticleRendererException("The GPU could not read the particle surface size (${eglError()}).")
    }
    return width[0] to height[0]
  }

  private fun destroyEnvironment(
    display: EGLDisplay,
    context: EGLContext,
    surface: EGLSurface
  ) {
    if (display == EGL14.EGL_NO_DISPLAY) {
      return
    }
    EGL14.eglMakeCurrent(
      display,
      EGL14.EGL_NO_SURFACE,
      EGL14.EGL_NO_SURFACE,
      EGL14.EGL_NO_CONTEXT
    )
    if (surface != EGL14.EGL_NO_SURFACE) {
      EGL14.eglDestroySurface(display, surface)
    }
    if (context != EGL14.EGL_NO_CONTEXT) {
      EGL14.eglDestroyContext(display, context)
    }
    EGL14.eglReleaseThread()
    EGL14.eglTerminate(display)
  }

  private fun currentState(): ParticleRenderState? {
    synchronized(commandLock) {
      return latestState
    }
  }

  private fun isStillRunning(): Boolean {
    synchronized(commandLock) {
      return shouldRun
    }
  }

  private fun recyclePendingBitmaps() {
    val pending: ParticleBitmapPair?
    synchronized(commandLock) {
      pending = pendingBitmaps
      pendingBitmaps = null
    }
    pending?.recycle()
  }

  private fun releaseWindowTexture() {
    try {
      windowTexture.release()
    } catch (_: Throwable) {
      // SurfaceTexture.release is idempotent on supported Android versions.
    }
  }

  private data class EglEnvironment(
    val display: EGLDisplay,
    val context: EGLContext,
    val surface: EGLSurface
  )

  private data class RenderCommand(
    val state: ParticleRenderState?,
    val bitmaps: ParticleBitmapPair?
  )
}

private fun eglError(): String {
  return "EGL error 0x${EGL14.eglGetError().toString(16)}"
}
