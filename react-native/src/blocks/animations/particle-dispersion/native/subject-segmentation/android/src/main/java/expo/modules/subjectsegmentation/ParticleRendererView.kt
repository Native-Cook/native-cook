package expo.modules.subjectsegmentation

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import android.view.ViewGroup
import androidx.annotation.UiThread
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * TextureView host for the GLES particle renderer. Reanimated remains the only
 * clock; setters collect a prop batch and applyProps submits only its newest state.
 */
@SuppressLint("ViewConstructor")
internal class ParticleRendererView(
  context: Context,
  appContext: AppContext
) : ExpoView(context, appContext),
  TextureView.SurfaceTextureListener,
  EglRenderThreadListener {
  companion object {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val decodeExecutor: ExecutorService = Executors.newFixedThreadPool(2) { runnable ->
      Thread(runnable, "SubjectSegmentation.TextureDecode").apply {
        isDaemon = true
      }
    }
  }

  val onReady by EventDispatcher<Map<String, Any>>()
  val onError by EventDispatcher<Map<String, Any>>()

  private val textureView = TextureView(context)

  private var frameUri = ""
  private var maskUri = ""
  private var timeSeconds = 0.0
  private var progress = 0.0
  private var dustStartSeconds = 0.417
  private var dustDurationSeconds = 2.6
  private var seed = 1.0
  private var reduceMotion = false
  private var edgeParticleStrength = 0.0
  private var particleBackgroundColor = floatArrayOf(0f, 0f, 0f, 1f)
  private var particleSubjectBounds = floatArrayOf(0f, 0f, 1f, 1f)

  private var generation = 0L
  private var currentIdentity: ParticleFrameIdentity? = null
  private var readyGeneration = Long.MIN_VALUE
  private var errorGeneration = Long.MIN_VALUE
  private var decodeGeneration = Long.MIN_VALUE
  private var decodeFuture: Future<*>? = null
  private var renderThread: EglRenderThread? = null
  private var activeWindowTexture: SurfaceTexture? = null
  private var surfaceWidth = 0
  private var surfaceHeight = 0
  private var destroyed = false

  init {
    clipChildren = true
    clipToPadding = true
    isClickable = false
    isFocusable = false
    setBackgroundColor(Color.TRANSPARENT)

    textureView.alpha = 0f
    textureView.isOpaque = false
    textureView.isClickable = false
    textureView.isFocusable = false
    textureView.surfaceTextureListener = this
    addView(
      textureView,
      ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
    )
  }

  fun setFrameUri(value: String) {
    frameUri = value
  }

  fun setMaskUri(value: String) {
    maskUri = value
  }

  fun setTimeSeconds(value: Double) {
    timeSeconds = value
  }

  fun setProgress(value: Double) {
    progress = value
  }

  fun setDustStartSeconds(value: Double) {
    dustStartSeconds = value
  }

  fun setDustDurationSeconds(value: Double) {
    dustDurationSeconds = value
  }

  fun setSeed(value: Double) {
    seed = value
  }

  fun setReduceMotion(value: Boolean) {
    reduceMotion = value
  }

  fun setParticleBackgroundColor(values: List<Double>) {
    if (values.size != 4 || values.any { !it.isFinite() }) {
      return
    }
    particleBackgroundColor = FloatArray(4) { index ->
      values[index].coerceIn(0.0, 1.0).toFloat()
    }
  }

  fun setParticleSubjectBounds(values: List<Double>) {
    if (values.size != 4 || values.any { !it.isFinite() }) {
      return
    }
    val x = values[0].coerceIn(0.0, 1.0)
    val y = values[1].coerceIn(0.0, 1.0)
    particleSubjectBounds = floatArrayOf(
      x.toFloat(),
      y.toFloat(),
      values[2].coerceIn(0.0, 1.0 - x).toFloat(),
      values[3].coerceIn(0.0, 1.0 - y).toFloat()
    )
  }

  fun setEdgeParticleStrength(value: Double) {
    edgeParticleStrength = value
  }

  /** Called by Expo once after all props in one native update have landed. */
  @UiThread
  fun applyProps() {
    if (Looper.myLooper() !== Looper.getMainLooper()) {
      mainHandler.post { applyProps() }
      return
    }
    if (destroyed) {
      return
    }

    val identity = if (frameUri.isNotEmpty() && maskUri.isNotEmpty()) {
      ParticleFrameIdentity(frameUri, maskUri)
    } else {
      null
    }
    if (identity == null) {
      if (currentIdentity != null) {
        invalidateCurrentIdentity()
        stopRenderThread(releaseWindowTexture = false)
      }
      return
    }

    if (identity != currentIdentity) {
      beginIdentity(identity)
    } else {
      submitLatestState()
    }
  }

  /** Idempotent deterministic cleanup used by Expo's Android OnViewDestroys hook. */
  @UiThread
  fun destroy() {
    if (Looper.myLooper() !== Looper.getMainLooper()) {
      mainHandler.post { destroy() }
      return
    }
    if (destroyed) {
      return
    }
    destroyed = true
    invalidateCurrentIdentity()
    stopRenderThread(releaseWindowTexture = false)
    textureView.alpha = 0f
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (destroyed) {
      return
    }
    if (textureView.isAvailable) {
      textureView.surfaceTexture?.let { windowTexture ->
        handleWindowTextureAvailable(windowTexture, textureView.width, textureView.height)
      }
    }
    applyProps()
  }

  override fun onDetachedFromWindow() {
    invalidateCurrentIdentity()
    stopRenderThread(releaseWindowTexture = false)
    super.onDetachedFromWindow()
  }

  override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
    super.onSizeChanged(width, height, oldWidth, oldHeight)
    if (width != oldWidth || height != oldHeight) {
      submitLatestState()
    }
  }

  override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
    handleWindowTextureAvailable(surface, width, height)
    // Surface recreation can happen without another JavaScript prop batch.
    applyProps()
  }

  override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
    if (surface !== activeWindowTexture) {
      return
    }
    surfaceWidth = width
    surfaceHeight = height
    submitLatestState()
  }

  override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
    if (surface !== activeWindowTexture) {
      return true
    }
    activeWindowTexture = null
    surfaceWidth = 0
    surfaceHeight = 0
    invalidateCurrentIdentity()
    val thread = renderThread
    renderThread = null
    if (thread != null) {
      thread.shutdown(releaseWindowTexture = true)
      // TextureView transferred ownership; the EGL thread releases after teardown.
      return false
    }
    return true
  }

  override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

  override fun onFramePresented(
    thread: EglRenderThread,
    generation: Long,
    identity: ParticleFrameIdentity
  ) {
    mainHandler.post {
      if (!isCurrent(thread, generation, identity)) {
        return@post
      }
      if (readyGeneration == generation || errorGeneration == generation) {
        return@post
      }
      readyGeneration = generation
      textureView.alpha = 1f
      onReady(mapOf("frameUri" to identity.frameUri, "maskUri" to identity.maskUri))
    }
  }

  override fun onRenderError(
    thread: EglRenderThread,
    generation: Long,
    identity: ParticleFrameIdentity,
    message: String
  ) {
    mainHandler.post {
      if (renderThread !== thread) {
        return@post
      }
      reportError(generation, identity, message)
    }
  }

  override fun onRenderThreadStopped(thread: EglRenderThread) {
    mainHandler.post {
      if (renderThread !== thread) {
        return@post
      }
      renderThread = null
      if (
        !destroyed && isAttachedToWindow &&
        currentIdentity != null && errorGeneration != generation &&
        activeWindowTexture != null
      ) {
        cancelDecode()
        ensureRenderThread()
        submitLatestState()
        scheduleDecode()
      }
    }
  }

  private fun beginIdentity(identity: ParticleFrameIdentity) {
    generation += 1
    cancelDecode()
    currentIdentity = identity
    readyGeneration = Long.MIN_VALUE
    errorGeneration = Long.MIN_VALUE
    textureView.alpha = 0f
    ensureRenderThread()
    submitLatestState()
    scheduleDecode()
  }

  private fun invalidateCurrentIdentity() {
    if (currentIdentity == null && decodeFuture == null) {
      return
    }
    generation += 1
    cancelDecode()
    currentIdentity = null
    readyGeneration = Long.MIN_VALUE
    errorGeneration = Long.MIN_VALUE
    textureView.alpha = 0f
  }

  private fun handleWindowTextureAvailable(
    windowTexture: SurfaceTexture,
    width: Int,
    height: Int
  ) {
    if (destroyed || !isAttachedToWindow) {
      return
    }
    if (activeWindowTexture !== windowTexture) {
      if (activeWindowTexture != null) {
        invalidateCurrentIdentity()
      }
      val obsoleteThread = renderThread
      renderThread = null
      obsoleteThread?.shutdown(releaseWindowTexture = false)
      activeWindowTexture = windowTexture
      surfaceWidth = width
      surfaceHeight = height
    } else {
      surfaceWidth = width
      surfaceHeight = height
    }
    ensureRenderThread()
    submitLatestState()
    scheduleDecode()
  }

  private fun ensureRenderThread() {
    if (
      destroyed || !isAttachedToWindow || activeWindowTexture == null ||
      surfaceWidth <= 0 || surfaceHeight <= 0 || currentIdentity == null
    ) {
      return
    }
    val existing = renderThread
    if (existing != null) {
      return
    }

    val thread = EglRenderThread(activeWindowTexture!!, this)
    renderThread = thread
    buildRenderState()?.let { thread.submitState(it) }
    thread.start()
  }

  private fun stopRenderThread(releaseWindowTexture: Boolean) {
    renderThread?.shutdown(releaseWindowTexture)
  }

  private fun submitLatestState() {
    if (
      destroyed || !isAttachedToWindow || currentIdentity == null ||
      errorGeneration == generation
    ) {
      return
    }
    ensureRenderThread()
    val thread = renderThread ?: return
    if (!thread.isAcceptingCommands) {
      return
    }
    val state = buildRenderState() ?: return
    thread.submitState(state)
  }

  private fun buildRenderState(): ParticleRenderState? {
    val identity = currentIdentity ?: return null
    val drawableWidth = if (textureView.width > 0) textureView.width else surfaceWidth
    val drawableHeight = if (textureView.height > 0) textureView.height else surfaceHeight
    val density = resources.displayMetrics.density.takeIf { it.isFinite() && it > 0f } ?: 1f
    val logicalWidth = drawableWidth.toFloat() / density
    val logicalHeight = drawableHeight.toFloat() / density
    if (logicalWidth <= 0f || logicalHeight <= 0f) {
      return null
    }

    val safeTime = finiteFloat(timeSeconds, 0f)
    val safeProgress = finiteFloat(progress, 0f).coerceIn(0f, 1f)
    val safeDustStart = finiteFloat(dustStartSeconds, 0.417f)
    val safeDustDuration = finiteFloat(dustDurationSeconds, 2.6f).coerceAtLeast(0.001f)
    val safeSeed = finiteFloat(seed, 1f)
    val safeEdgeStrength = finiteFloat(edgeParticleStrength, 0f).coerceIn(0f, 1f)
    return ParticleRenderState(
      generation = generation,
      identity = identity,
      logicalWidth = logicalWidth,
      logicalHeight = logicalHeight,
      timeSeconds = safeTime,
      progress = safeProgress,
      dustStartSeconds = safeDustStart,
      dustDurationSeconds = safeDustDuration,
      seedBits = java.lang.Float.floatToRawIntBits(safeSeed),
      reduceMotion = reduceMotion,
      backgroundColor = particleBackgroundColor.copyOf(),
      subjectBounds = particleSubjectBounds.copyOf(),
      edgeParticleStrength = safeEdgeStrength
    )
  }

  private fun scheduleDecode() {
    val identity = currentIdentity ?: return
    val thread = renderThread ?: return
    if (
      destroyed || errorGeneration == generation || !thread.isAcceptingCommands ||
      decodeGeneration == generation
    ) {
      return
    }

    cancelDecode()
    val requestedGeneration = generation
    decodeGeneration = requestedGeneration
    val weakView = WeakReference(this)
    decodeFuture = decodeExecutor.submit {
      var decoded: ParticleBitmapPair? = null
      try {
        if (Thread.currentThread().isInterrupted) {
          throw CancellationException()
        }
        val frame = decodeLocalBitmap(identity.frameUri, "frame")
        try {
          val mask = decodeLocalBitmap(identity.maskUri, "mask")
          decoded = ParticleBitmapPair(requestedGeneration, identity, frame, mask)
        } catch (error: Throwable) {
          frame.recycle()
          throw error
        }
        if (Thread.currentThread().isInterrupted) {
          throw CancellationException()
        }

        val completed = requireNotNull(decoded)
        decoded = null
        mainHandler.post {
          val view = weakView.get()
          if (view == null) {
            completed.recycle()
            return@post
          }
          view.receiveDecodedBitmaps(thread, completed)
        }
      } catch (_: CancellationException) {
        decoded?.recycle()
      } catch (error: Throwable) {
        decoded?.recycle()
        mainHandler.post {
          val view = weakView.get() ?: return@post
          view.receiveDecodeError(
            requestedGeneration,
            identity,
            error.message ?: "Could not decode the particle images."
          )
        }
      }
    }
  }

  private fun receiveDecodedBitmaps(thread: EglRenderThread, bitmaps: ParticleBitmapPair) {
    if (decodeGeneration == bitmaps.generation) {
      decodeFuture = null
    }
    if (!isCurrent(thread, bitmaps.generation, bitmaps.identity)) {
      bitmaps.recycle()
      return
    }
    if (!thread.submitBitmaps(bitmaps)) {
      bitmaps.recycle()
    }
  }

  private fun receiveDecodeError(
    requestedGeneration: Long,
    identity: ParticleFrameIdentity,
    message: String
  ) {
    if (decodeGeneration == requestedGeneration) {
      decodeFuture = null
    }
    reportError(requestedGeneration, identity, message)
  }

  private fun reportError(
    requestedGeneration: Long,
    identity: ParticleFrameIdentity,
    message: String
  ) {
    if (
      destroyed || !isAttachedToWindow || generation != requestedGeneration ||
      currentIdentity != identity || frameUri != identity.frameUri || maskUri != identity.maskUri ||
      errorGeneration == requestedGeneration
    ) {
      return
    }
    errorGeneration = requestedGeneration
    cancelDecode()
    textureView.alpha = 0f
    onError(
      mapOf(
        "frameUri" to identity.frameUri,
        "maskUri" to identity.maskUri,
        "message" to message
      )
    )
  }

  private fun isCurrent(
    thread: EglRenderThread,
    requestedGeneration: Long,
    identity: ParticleFrameIdentity
  ): Boolean {
    return !destroyed && isAttachedToWindow && renderThread === thread &&
      generation == requestedGeneration && currentIdentity == identity &&
      frameUri == identity.frameUri && maskUri == identity.maskUri
  }

  private fun cancelDecode() {
    decodeFuture?.cancel(true)
    decodeFuture = null
    decodeGeneration = Long.MIN_VALUE
  }

  private fun finiteFloat(value: Double, fallback: Float): Float {
    val converted = value.toFloat()
    return if (converted.isFinite()) converted else fallback
  }
}

private fun decodeLocalBitmap(uriValue: String, kind: String): Bitmap {
  val file = readableLocalFile(uriValue)
  val options = BitmapFactory.Options().apply {
    inPreferredConfig = Bitmap.Config.ARGB_8888
    inScaled = false
  }
  val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
    ?: throw ParticleRendererException("Could not decode the particle $kind image.")
  if (decoded.width <= 0 || decoded.height <= 0) {
    decoded.recycle()
    throw ParticleRendererException("The particle $kind image has invalid dimensions.")
  }

  val needsSoftwareArgb = decoded.config != Bitmap.Config.ARGB_8888
  if (!needsSoftwareArgb) {
    return decoded
  }
  val converted = decoded.copy(Bitmap.Config.ARGB_8888, false)
  decoded.recycle()
  return converted
    ?: throw ParticleRendererException("Could not convert the particle $kind image for the GPU.")
}

private fun readableLocalFile(uriValue: String): File {
  val parsed = Uri.parse(uriValue)
  val path = when {
    parsed.scheme.equals("file", ignoreCase = true) &&
      (parsed.authority.isNullOrEmpty() || parsed.authority == "localhost") -> parsed.path
    parsed.scheme.isNullOrEmpty() && uriValue.startsWith('/') -> uriValue
    else -> null
  }
  val file = path?.let(::File)
  if (file == null || !file.isAbsolute || !file.isFile || !file.canRead()) {
    throw ParticleRendererException("Particle images must be readable local file URIs.")
  }
  return file
}
