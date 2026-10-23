package expo.modules.subjectsegmentation

import android.os.SystemClock
import android.util.Log
import expo.modules.kotlin.Promise
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.util.concurrent.Executors

class SubjectSegmentationModule : Module() {
  private val requestRegistry = SegmentationRequestRegistry()
  private val workerExecutor = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "SubjectSegmentation.Worker").apply { isDaemon = true }
  }
  private val workerDispatcher = workerExecutor.asCoroutineDispatcher()
  private val workerScope = CoroutineScope(
    SupervisorJob() + workerDispatcher + CoroutineName("SubjectSegmentation.Worker")
  )
  private val applicationContextDelegate = lazy {
    appContext.reactContext?.applicationContext
      ?: throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_IMAGE_RENDER",
        "The Android application context is unavailable."
      )
  }
  private val applicationContext
    get() = applicationContextDelegate.value
  private val imageProcessorDelegate = lazy { ImageFrameProcessor(applicationContext) }
  private val imageProcessor: ImageFrameProcessor
    get() = imageProcessorDelegate.value
  private val maskProcessorDelegate = lazy {
    SubjectMaskProcessor(applicationContext, imageProcessor)
  }
  private val maskProcessor: SubjectMaskProcessor
    get() = maskProcessorDelegate.value

  override fun definition() = ModuleDefinition {
    Name("SubjectSegmentation")

    Constant("particleRendererVersion") { 1 }
    Constant("performanceLoggingEnabled") { performanceLoggingEnabled }

    Function("markPerformance") { stage: String ->
      if (performanceLoggingEnabled && stage in PERFORMANCE_STAGES) {
        Log.i(
          LOG_TAG,
          "[ParticlePhase] stage=$stage uptime=${uptimeSeconds()}"
        )
      }
    }

    View(ParticleRendererView::class) {
      Events("onReady", "onError")

      Prop("frameUri", "") { view: ParticleRendererView, value: String ->
        view.setFrameUri(value)
      }
      Prop("maskUri", "") { view: ParticleRendererView, value: String ->
        view.setMaskUri(value)
      }
      Prop("timeSeconds", 0.0) { view: ParticleRendererView, value: Double ->
        view.setTimeSeconds(value)
      }
      Prop("progress", 0.0) { view: ParticleRendererView, value: Double ->
        view.setProgress(value)
      }
      Prop("dustStartSeconds", 0.417) { view: ParticleRendererView, value: Double ->
        view.setDustStartSeconds(value)
      }
      Prop("dustDurationSeconds", 2.6) { view: ParticleRendererView, value: Double ->
        view.setDustDurationSeconds(value)
      }
      Prop("seed", 1.0) { view: ParticleRendererView, value: Double ->
        view.setSeed(value)
      }
      Prop("reduceMotion", false) { view: ParticleRendererView, value: Boolean ->
        view.setReduceMotion(value)
      }
      Prop("backgroundColor", listOf(0.0, 0.0, 0.0, 1.0)) {
          view: ParticleRendererView,
          value: List<Double> ->
        view.setParticleBackgroundColor(value)
      }
      Prop("subjectBounds", listOf(0.0, 0.0, 1.0, 1.0)) {
          view: ParticleRendererView,
          value: List<Double> ->
        view.setParticleSubjectBounds(value)
      }
      Prop("edgeParticleStrength", 0.0) { view: ParticleRendererView, value: Double ->
        view.setEdgeParticleStrength(value)
      }

      OnViewDidUpdateProps { view: ParticleRendererView ->
        view.applyProps()
      }
      OnViewDestroys { view: ParticleRendererView ->
        view.destroy()
      }
    }

    // Heavy work is immediately dispatched off Expo's serial AsyncFunction queue.
    // This keeps cancel() responsive while ML Kit is running.
    AsyncFunction("prepareEffectFrame") {
        options: PrepareEffectFrameOptions,
        promise: Promise ->
      try {
        validateRequestId(options.requestId)
        launchRequest(options.requestId, "prepare-frame", promise) { token ->
          imageProcessor.prepare(options, token)
        }
      } catch (error: CodedException) {
        promise.reject(error)
      }
    }

    AsyncFunction("segmentEffectFrame") {
        options: SegmentEffectFrameOptions,
        promise: Promise ->
      try {
        validateRequestId(options.requestId)
        launchRequest(options.requestId, "segment-frame", promise) { token ->
          maskProcessor.segment(options, token)
        }
      } catch (error: CodedException) {
        promise.reject(error)
      }
    }

    AsyncFunction("prepareSubjectSegmentation") { promise: Promise ->
      workerScope.launch {
        try {
          maskProcessor.prepareModel()
          promise.resolve()
        } catch (error: CancellationException) {
          promise.reject(
            segmentationError(
              "ERR_SUBJECT_SEGMENTATION_MODEL_DOWNLOAD",
              "Subject isolation model preparation was interrupted. Try again.",
              error
            )
          )
        } catch (error: CodedException) {
          promise.reject(error)
        } catch (error: Throwable) {
          promise.reject(
            segmentationError(
              "ERR_SUBJECT_SEGMENTATION_MODEL_DOWNLOAD",
              "Could not prepare the on-device subject isolation model. Check Google Play services, then try again.",
              error
            )
          )
        }
      }
    }

    AsyncFunction("cancel") { requestId: String ->
      requestRegistry.cancel(requestId)
    }

    AsyncFunction("release") { uris: List<String>, promise: Promise ->
      workerScope.launch {
        try {
          imageProcessor.releaseOwnedFiles(uris)
          promise.resolve()
        } catch (error: CodedException) {
          promise.reject(error)
        } catch (error: Throwable) {
          promise.reject(
            segmentationError(
              "ERR_SUBJECT_SEGMENTATION_RELEASE",
              "Could not release the subject segmentation cache files.",
              error
            )
          )
        }
      }
    }

    OnDestroy {
      requestRegistry.cancelAll()
      if (maskProcessorDelegate.isInitialized()) {
        maskProcessorDelegate.value.close()
      }
      workerScope.cancel()
      workerDispatcher.close()
    }
  }

  private fun launchRequest(
    requestId: String,
    stage: String,
    promise: Promise,
    operation: suspend (SegmentationRequestToken) -> Map<String, Any?>
  ) {
    val token = requestRegistry.register(requestId)
    // Enter the guarded body before returning the Job so cancellation cannot
    // prevent promise settlement or registry cleanup while the work is queued.
    val job = workerScope.launch(start = CoroutineStart.UNDISPATCHED) {
      val started = SystemClock.elapsedRealtimeNanos()
      var completedResult: Map<String, Any?>? = null
      try {
        yield()
        token.throwIfCancelled()
        val result = operation(token)
        completedResult = result
        token.throwIfCancelled()
        if (!token.tryFinish()) {
          throw SegmentationCancelledException(requestId)
        }
        promise.resolve(result)
      } catch (error: CancellationException) {
        releaseResultArtifacts(completedResult)
        rejectCancellation(promise, requestId, error)
      } catch (error: CodedException) {
        if (token.isCancelled) {
          releaseResultArtifacts(completedResult)
          rejectCancellation(promise, requestId, error)
        } else if (token.tryFinish()) {
          promise.reject(error)
        } else {
          releaseResultArtifacts(completedResult)
          rejectCancellation(promise, requestId, error)
        }
      } catch (error: Throwable) {
        if (token.isCancelled) {
          releaseResultArtifacts(completedResult)
          rejectCancellation(promise, requestId, error)
        } else if (token.tryFinish()) {
          val code = if (stage == "prepare-frame") {
            "ERR_SUBJECT_SEGMENTATION_IMAGE_RENDER"
          } else {
            "ERR_SUBJECT_SEGMENTATION_VISION"
          }
          promise.reject(
            segmentationError(
              code,
              "Subject segmentation failed during '$stage'.",
              error
            )
          )
        } else {
          releaseResultArtifacts(completedResult)
          rejectCancellation(promise, requestId, error)
        }
      } finally {
        requestRegistry.unregister(token)
        logDuration(stage, started)
      }
    }
    token.attach(job)
  }

  private fun releaseResultArtifacts(result: Map<String, Any?>?) {
    if (result == null || !imageProcessorDelegate.isInitialized()) return
    val uris = listOfNotNull(
      result["frameUri"] as? String,
      result["maskUri"] as? String
    )
    if (uris.isEmpty()) return
    try {
      imageProcessorDelegate.value.releaseOwnedFiles(uris)
    } catch (_: Throwable) {
      // Best-effort cleanup must not replace the cancellation error.
    }
  }

  private fun rejectCancellation(
    promise: Promise,
    requestId: String,
    cause: Throwable?
  ) {
    promise.reject(
      segmentationError(
        CANCELLED_ERROR_CODE,
        "Subject segmentation request '$requestId' was cancelled.",
        cause
      )
    )
  }

  private fun validateRequestId(requestId: String) {
    if (requestId.isBlank()) {
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_REQUEST_ID",
        "requestId must be a non-empty string."
      )
    }
  }

  private fun logDuration(stage: String, started: Long) {
    if (!performanceLoggingEnabled) return
    val finished = SystemClock.elapsedRealtimeNanos()
    val durationMilliseconds = (finished - started) / 1_000_000.0
    Log.i(
      LOG_TAG,
      "[ParticlePhase] stage=$stage uptime=${finished / 1_000_000_000.0} " +
        "durationMs=$durationMilliseconds"
    )
  }

  private val performanceLoggingEnabled: Boolean
    get() = System.getenv("PARTICLE_PERF_LOG") == "1"

  private fun uptimeSeconds(): Double = SystemClock.elapsedRealtimeNanos() / 1_000_000_000.0

  companion object {
    private const val LOG_TAG = "SubjectSegmentation"
    private val PERFORMANCE_STAGES = setOf(
      "frame-decoded",
      "playback-start",
      "playback-end",
      "playback-error"
    )
  }
}
