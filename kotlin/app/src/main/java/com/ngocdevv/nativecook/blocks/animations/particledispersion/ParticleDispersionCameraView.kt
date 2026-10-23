package com.ngocdevv.nativecook.blocks.animations.particledispersion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.util.Rational
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import kotlinx.coroutines.CoroutineScope
import com.ngocdevv.nativecook.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class ParticleDispersionCameraView(
  private val activity: ComponentActivity,
  private val owner: LifecycleOwner,
  private val scope: CoroutineScope,
  private val requestPermission: () -> Unit
) : FrameLayout(activity), DefaultLifecycleObserver {
  private enum class Phase { PREVIEW, CAPTURING, ANALYZING, EFFECT, RESULT, ERROR }
  private val root: FrameLayout = this
  private val date = TextView(context)
  private lateinit var preview: PreviewView
  private lateinit var canvas: DispersionView
  private lateinit var dock: CameraDock
  private lateinit var motionButton: TextView
  private lateinit var imageProcessor: ImageFrameProcessor
  private lateinit var subjectProcessor: SubjectMaskProcessor
  private val requests = SegmentationRequestRegistry()
  private var requestID = UUID.randomUUID().toString()
  private var processingJob: Job? = null
  private var modelJob: Job? = null
  private var provider: ProcessCameraProvider? = null
  private var capture: ImageCapture? = null
  private var boundCamera: Camera? = null
  private var scene: EffectScene? = null
  private var phase = Phase.PREVIEW
  private var reduceOverride = false
  private var modelReady = false
  private var sceneReady = false
  private var cameraReady = false
  private var active = false
  private var message = ""
  private var pendingPlayback = false
  private var closed = false

  init {
    imageProcessor = ImageFrameProcessor(context)
    subjectProcessor = SubjectMaskProcessor(context, imageProcessor)
    setBackgroundColor(Color.BLACK)
    preview = PreviewView(context).apply {
      implementationMode = PreviewView.ImplementationMode.COMPATIBLE
      scaleType = PreviewView.ScaleType.FILL_CENTER
    }
    configureCameraGestures()
    canvas = DispersionView(context)
    root.addView(preview, FrameLayout.LayoutParams(-1, -1))
    root.addView(canvas, FrameLayout.LayoutParams(-1, -1))
    date.apply {
      text = SimpleDateFormat("MMM d", Locale.US).format(Date())
      textSize = 20f
      setTextColor(Color.WHITE)
      typeface = Typeface.create("serif", Typeface.NORMAL)
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    root.addView(date, FrameLayout.LayoutParams(dp(140), dp(30), Gravity.TOP or Gravity.START).apply {
      leftMargin = dp(24); topMargin = dp(34)
    })
    motionButton = pill("Reduce motion: Off", R.id.particledispersion_reduced_motion_toggle) { reduceOverride = !reduceOverride; updateControls() }
    root.addView(motionButton, FrameLayout.LayoutParams(dp(156), dp(32), Gravity.TOP or Gravity.END).apply {
      rightMargin = dp(24); topMargin = dp(34)
    })
    dock = CameraDock(context).apply {
      onPrimary = { primaryAction() }
      onRetake = { retake() }
    }
    root.addView(dock, FrameLayout.LayoutParams(-1, dp(224), Gravity.BOTTOM))
    canvas.onComplete = { if (phase == Phase.EFFECT) setPhase(Phase.RESULT) }
    canvas.onError = { showError(it) }
    canvas.onReady = {
      sceneReady = true
      if (pendingPlayback) { pendingPlayback = false; play() }
      else updateControls()
    }
    updateControls()
  }

  // The Compose host supplies safe-area pixels: AndroidView may receive consumed insets.
  fun updateInsets(top: Int, bottom: Int) {
    for (header in listOf(date, motionButton)) {
      val params = header.layoutParams as LayoutParams
      if (params.topMargin != top + dp(34)) {
        params.topMargin = top + dp(34)
        header.layoutParams = params
      }
    }
    val params = dock.layoutParams as LayoutParams
    val height = max(dp(224), dp(190) + bottom)
    if (params.height != height) {
      params.height = height
      dock.layoutParams = params
    }
  }

  private fun pill(title: String, resourceID: Int, action: () -> Unit) = TextView(context).apply {
    id = resourceID; text = title; textSize = 12f; setTextColor(Color.WHITE)
    gravity = Gravity.CENTER
    background = GradientDrawable().apply { setColor(0x48000000); cornerRadius = dp(16).toFloat() }
    isClickable = true; isFocusable = true
    setOnClickListener { action() }
  }

  private fun setPhase(value: Phase) { phase = value; updateControls() }
  private fun reducedMotion() = reduceOverride || Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

  private fun updateControls() {
    motionButton.text = "Reduce motion: ${if (reducedMotion()) "On" else "Off"}"
    motionButton.contentDescription = "Reduced motion: ${if (reducedMotion()) "On" else "Off"}"
    motionButton.isEnabled = phase in listOf(Phase.PREVIEW, Phase.RESULT, Phase.ERROR)
    val text = when (phase) {
      Phase.PREVIEW -> if (!modelReady) "Preparing subject isolation…\nThis may take a moment the first time"
        else if (!cameraReady) "Opening camera…\nPlease wait a moment"
        else "Center your subject in the frame\nTap to capture!"
      Phase.CAPTURING -> "Hold still for a moment…\nTaking your photo"
      Phase.ANALYZING -> "Finding your subject…\nEverything stays on your device"
      Phase.EFFECT -> "The background is turning into particles…\nYour effect will be ready in a few seconds"
      Phase.RESULT -> "Tap the checkmark to replay\nTap cancel to take another photo"
      Phase.ERROR -> message
    }
    val enabled = phase == Phase.RESULT || phase == Phase.ERROR ||
      (phase == Phase.PREVIEW && cameraReady && modelReady)
    dock.update(text, phase == Phase.RESULT, enabled, phase == Phase.ERROR, reducedMotion())
  }

  private fun startCamera() {
    if (!active || closed) return
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      if (activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
        showError(IllegalStateException("Camera access is needed. Tap to allow it."))
      } else requestPermission()
      return
    }
    val generation = requestID
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener({
      if (requestID != generation || !active || closed) return@addListener
      try {
        provider = future.get()
        val cameraPreview = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
        val photo = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
          .setTargetRotation(Surface.ROTATION_0).build()
        val viewPort = ViewPort.Builder(Rational(max(root.width, 1), max(root.height, 1)), Surface.ROTATION_0)
          .setScaleType(ViewPort.FILL_CENTER).build()
        provider?.unbindAll()
        boundCamera = provider?.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA,
          UseCaseGroup.Builder().addUseCase(cameraPreview).addUseCase(photo).setViewPort(viewPort).build())
        capture = photo
        cameraReady = true
        canvas.visibility = View.GONE
        scene?.release(); scene = null
        updateControls()
        prepareModel(generation)
      } catch (error: Throwable) { showError(error) }
    }, ContextCompat.getMainExecutor(context))
  }

  private fun configureCameraGestures() {
    val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
      override fun onScale(detector: ScaleGestureDetector): Boolean {
        val camera = boundCamera ?: return false
        val zoom = camera.cameraInfo.zoomState.value ?: return false
        camera.cameraControl.setZoomRatio((zoom.zoomRatio * detector.scaleFactor)
          .coerceIn(zoom.minZoomRatio, min(zoom.maxZoomRatio, 8f)))
        return true
      }
    })
    val tap = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
      override fun onDown(event: MotionEvent) = true
      override fun onSingleTapUp(event: MotionEvent): Boolean {
        val point = preview.meteringPointFactory.createPoint(event.x, event.y)
        boundCamera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point,
          FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE).build())
        preview.performClick()
        return true
      }
    })
    preview.setOnTouchListener { _, event ->
      if (phase != Phase.PREVIEW) false else {
        scale.onTouchEvent(event)
        if (!scale.isInProgress && event.pointerCount == 1) tap.onTouchEvent(event)
        true
      }
    }
  }

  private fun prepareModel(generation: String) {
    if (modelReady || modelJob?.isActive == true) return
    modelJob = scope.launch {
      try {
        withContext(Dispatchers.IO) { subjectProcessor.prepareModel() }
        if (requestID == generation) { modelReady = true; updateControls() }
      } catch (error: Throwable) {
        if (requestID == generation && error !is kotlinx.coroutines.CancellationException) showError(error)
      }
    }
  }

  private fun primaryAction() {
    dock.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM
      else android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    if (phase == Phase.ERROR) {
      if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        if (activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) requestPermission()
        else activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
      } else retake()
      return
    }
    if (phase == Phase.RESULT) { play(); return }
    if (phase != Phase.PREVIEW || !cameraReady || !modelReady) return
    setPhase(Phase.CAPTURING)
    val generation = requestID
    val source = File(context.cacheDir, "capture-${UUID.randomUUID()}.jpg")
    capture?.takePicture(ImageCapture.OutputFileOptions.Builder(source).build(),
      ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
          if (requestID != generation) { source.delete(); return }
          process(source, generation)
        }
        override fun onError(error: ImageCaptureException) {
          source.delete()
          if (requestID == generation) showError(error)
        }
      })
  }

  private fun process(source: File, generation: String) {
    setPhase(Phase.ANALYZING)
    val scale = min(1f, 1600f / max(root.width, root.height))
    val width = max(2, (root.width * scale).roundToInt() / 2 * 2)
    val height = max(2, (root.height * scale).roundToInt() / 2 * 2)
    val token = requests.register(generation)
    processingJob = scope.launch {
      var prepared: EffectScene? = null
      val owned = mutableListOf<String>()
      try {
        prepared = withContext(Dispatchers.IO) {
          val frame = imageProcessor.prepare(PrepareEffectFrameOptions().apply {
            sourceUri = source.absolutePath; requestId = generation; targetWidth = width; targetHeight = height
          }, token)
          val frameURI = frame["frameUri"] as String
          owned.add(frameURI)
          val mask = subjectProcessor.segment(SegmentEffectFrameOptions().apply {
            frameUri = frameURI; requestId = generation
          }, token)
          val maskURI = mask["maskUri"] as String
          owned.add(maskURI)
          @Suppress("UNCHECKED_CAST") val bounds = mask["subjectBounds"] as Map<String, Number>
          val x = bounds.getValue("x").toFloat(); val y = bounds.getValue("y").toFloat()
          EffectScene(imageProcessor.localFile(frameURI), imageProcessor.localFile(maskURI), width, height,
            RectF(x, y, x + bounds.getValue("width").toFloat(), y + bounds.getValue("height").toFloat()), true)
        }
        if (requestID != generation) { prepared.release(); return@launch }
        sceneReady = false
        pendingPlayback = true
        canvas.visibility = View.VISIBLE
        canvas.show(prepared)
        scene?.release(); scene = prepared
        provider?.unbindAll(); cameraReady = false
      } catch (error: Throwable) {
        prepared?.release()
        withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { imageProcessor.releaseOwnedFiles(owned) }
        if (requestID == generation && error !is kotlinx.coroutines.CancellationException) showError(error)
      } finally { source.delete(); requests.unregister(token) }
    }
    token.attach(processingJob!!)
  }

  private fun play() {
    if (!sceneReady) return
    setPhase(Phase.EFFECT)
    canvas.play(reducedMotion())
    if (!active) canvas.suspendPlayback()
  }

  private fun retake() {
    requests.cancel(requestID)
    requestID = UUID.randomUUID().toString()
    processingJob?.cancel(); modelJob?.cancel()
    pendingPlayback = false
    canvas.stop()
    setPhase(Phase.PREVIEW)
    startCamera()
  }

  private fun showError(error: Throwable) {
    canvas.stop()
    pendingPlayback = false
    Log.e("ParticleNative", error.message ?: "The effect could not be created.", error)
    message = when {
      error is NativeProcessingException && error.code.contains("NO_SUBJECT") ->
        "No clear subject was found\nTry another subject"
      error is NativeProcessingException && error.code.contains("MODEL_STORAGE") ->
        "Subject isolation needs more storage\nFree up space"
      error is NativeProcessingException && error.code.contains("MODEL_DOWNLOAD") ->
        "Could not prepare subject isolation\nCheck your connection"
      error is NativeProcessingException ->
        "Could not prepare subject isolation\nTry again"
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ->
        "Camera access is needed\nTap to allow it"
      else -> "Could not create the effect\nTap to retry"
    }
    setPhase(Phase.ERROR)
  }

  fun onCameraPermission(granted: Boolean) {
    if (closed) return
    if (granted) startCamera()
    else showError(IllegalStateException("Camera access is disabled. Enable it in Settings."))
  }

  override fun onResume(owner: LifecycleOwner) {
    if (closed) return
    active = true
    canvas.onResume()
    canvas.resumePlayback()
    if (phase in listOf(Phase.PREVIEW, Phase.ERROR)) {
      setPhase(Phase.PREVIEW)
      root.post { startCamera() }
    }
  }

  override fun onPause(owner: LifecycleOwner) {
    active = false
    cameraReady = false
    provider?.unbindAll()
    canvas.suspendPlayback()
    canvas.onPause()
  }

  fun close() {
    if (closed) return
    closed = true
    active = false
    requests.cancelAll()
    requestID = UUID.randomUUID().toString()
    processingJob?.cancel()
    modelJob?.cancel()
    canvas.onComplete = null
    canvas.onReady = null
    canvas.onError = null
    canvas.stop()
    provider?.unbindAll()
    subjectProcessor.close()
    scene?.release()
    scene = null
    owner.lifecycle.removeObserver(this)
  }

  private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

}
