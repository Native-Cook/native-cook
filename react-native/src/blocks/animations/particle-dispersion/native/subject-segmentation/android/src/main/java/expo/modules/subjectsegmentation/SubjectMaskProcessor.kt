package expo.modules.subjectsegmentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.Subject
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import expo.modules.kotlin.exception.CodedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.FloatBuffer
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

private data class ReadableSubjectMask(
  val startX: Int,
  val startY: Int,
  val width: Int,
  val height: Int,
  val confidence: FloatArray
)

internal class SubjectMaskProcessor(
  context: Context,
  private val imageProcessor: ImageFrameProcessor
) {
  private val modelInstaller = SubjectModelInstaller(context)
  private val clientLock = Any()

  @Volatile
  private var closed = false

  @Volatile
  private var client: SubjectSegmenter? = null

  private var activeClientOperations = 0

  suspend fun segment(
    options: SegmentEffectFrameOptions,
    token: SegmentationRequestToken
  ): Map<String, Any?> {
    token.throwIfCancelled()
    val frameFile = imageProcessor.localFile(options.frameUri)
    if (!frameFile.isFile || !frameFile.canRead()) {
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_FRAME_UNREADABLE",
        "The prepared effect frame is not readable at '${frameFile.path}'."
      )
    }

    prepareModel(token)
    token.throwIfCancelled()

    val frameBitmap = try {
      BitmapFactory.decodeFile(
        frameFile.path,
        BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
      )
    } catch (error: OutOfMemoryError) {
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_IMAGE_DECODE",
        "The prepared effect frame is too large to decode.",
        error
      )
    } ?: throw segmentationError(
      "ERR_SUBJECT_SEGMENTATION_IMAGE_DECODE",
      "The prepared effect frame could not be decoded."
    )
    val frameWidth = frameBitmap.width
    val frameHeight = frameBitmap.height
    if (frameWidth <= 0 || frameHeight <= 0) {
      frameBitmap.recycle()
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_IMAGE_EMPTY",
        "The prepared effect frame has invalid dimensions."
      )
    }

    val bitmapRecycled = AtomicBoolean(false)
    val clientOperationReleased = AtomicBoolean(false)
    var processOwnsBitmap = false
    var clientOperationAcquired = false
    var clientOperationTransferred = false
    var activeMlTask: Task<*>? = null
    fun recycleBitmapOnce() {
      if (bitmapRecycled.compareAndSet(false, true)) {
        frameBitmap.recycle()
      }
    }
    fun releaseClientOperationOnce() {
      if (clientOperationAcquired && clientOperationReleased.compareAndSet(false, true)) {
        releaseClientOperation()
      }
    }

    val result = try {
      token.throwIfCancelled()
      val segmenter = acquireSubjectSegmenter().also {
        clientOperationAcquired = true
      }
      token.throwIfCancelled()

      try {
        val processTask = segmenter.process(InputImage.fromBitmap(frameBitmap, 0))
        activeMlTask = processTask
        // Google Tasks cannot be cancelled through coroutine cancellation. Once
        // process() accepts the InputImage, ML Kit owns the bitmap lease until
        // its Task settles, even if the JS promise is rejected immediately.
        processTask.addOnCompleteListener(DIRECT_EXECUTOR) {
          recycleBitmapOnce()
          releaseClientOperationOnce()
        }
        processOwnsBitmap = true
        clientOperationTransferred = true
        awaitTask(processTask, token)
      } catch (error: CancellationException) {
        throw error
      } catch (error: CodedException) {
        throw error
      } catch (error: Throwable) {
        throw mlKitError("initialize the model or generate a foreground instance mask", error)
      }
    } finally {
      if (!processOwnsBitmap) {
        recycleBitmapOnce()
      }
      if (clientOperationAcquired && !clientOperationTransferred) {
        val task = activeMlTask
        if (task != null && !task.isComplete) {
          task.addOnCompleteListener(DIRECT_EXECUTOR) {
            releaseClientOperationOnce()
          }
        } else {
          releaseClientOperationOnce()
        }
      }
    }

    token.throwIfCancelled()
    if (result.subjects.isEmpty()) {
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_NO_SUBJECT",
        "ML Kit did not find a separable foreground subject."
      )
    }

    val subjectMasks = readableSubjectMasks(result.subjects, token)
    if (subjectMasks.isEmpty()) {
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_MASK_ACCESS",
        "ML Kit did not expose a readable confidence mask for any detected subject."
      )
    }

    val selectionX = normalizedSelection(options.selectionX)
    val selectionY = normalizedSelection(options.selectionY)
    val selectionPixelX = (selectionX * (frameWidth - 1)).roundToInt().coerceIn(0, frameWidth - 1)
    val selectionPixelY = (selectionY * (frameHeight - 1)).roundToInt().coerceIn(0, frameHeight - 1)

    val directlySelected = subjectAt(subjectMasks, selectionPixelX, selectionPixelY)
    val selected = directlySelected
      ?: nearestSubject(subjectMasks, selectionPixelX, selectionPixelY, 0.5f, token)
      ?: nearestSubject(subjectMasks, selectionPixelX, selectionPixelY, 0.0f, token)
      ?: subjectMasks.first()
    val selectionFallback = directlySelected == null

    val luminance = fullFrameMask(selected, frameWidth, frameHeight, token)
    val geometry = MaskGeometry.analyze(luminance, frameWidth, frameHeight, token)
    val output = imageProcessor.writeMask(luminance, frameWidth, frameHeight, token)

    return mapOf(
      "requestId" to options.requestId,
      "maskUri" to Uri.fromFile(output).toString(),
      "width" to frameWidth,
      "height" to frameHeight,
      "selectedInstanceCount" to 1,
      "selectionFallback" to selectionFallback,
      "subjectBounds" to geometry.bounds.asMap(),
      "contourPoints" to geometry.contour.map(NormalizedPoint::asMap)
    )
  }

  suspend fun prepareModel(token: SegmentationRequestToken? = null) {
    token?.throwIfCancelled()
    val segmenter = acquireSubjectSegmenter()
    try {
      modelInstaller.ensureAvailable(segmenter)
      token?.throwIfCancelled()
    } finally {
      releaseClientOperation()
    }
  }

  fun close() {
    val segmenterToClose = synchronized(clientLock) {
      closed = true
      if (activeClientOperations == 0) {
        client.also { client = null }
      } else {
        null
      }
    }
    segmenterToClose?.close()
  }

  private fun acquireSubjectSegmenter(): SubjectSegmenter = synchronized(clientLock) {
    if (closed) {
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_VISION",
        "ML Kit subject segmentation is unavailable because the native module was destroyed."
      )
    }
    (
      client ?: try {
        val subjectOptions = SubjectSegmenterOptions.SubjectResultOptions.Builder()
          .enableConfidenceMask()
          .build()
        val options = SubjectSegmenterOptions.Builder()
          .enableMultipleSubjects(subjectOptions)
          .build()
        SubjectSegmentation.getClient(options).also { client = it }
      } catch (error: Throwable) {
        throw mlKitError("create the subject segmentation client", error)
      }
    ).also { activeClientOperations += 1 }
  }

  private fun releaseClientOperation() {
    val segmenterToClose = synchronized(clientLock) {
      if (activeClientOperations > 0) {
        activeClientOperations -= 1
      }
      if (closed && activeClientOperations == 0) {
        client.also { client = null }
      } else {
        null
      }
    }
    segmenterToClose?.close()
  }

  private suspend fun <T> awaitTask(
    task: Task<T>,
    token: SegmentationRequestToken
  ): T = suspendCancellableCoroutine { continuation ->
    task
      .addOnSuccessListener { value ->
        if (continuation.isActive) continuation.resume(value)
      }
      .addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
      }
      .addOnCanceledListener {
        if (continuation.isActive) {
          if (token.isCancelled) {
            continuation.cancel(SegmentationCancelledException(token.requestId))
          } else {
            continuation.resumeWithException(
              IllegalStateException("The ML Kit task was cancelled before completion.")
            )
          }
        }
      }
  }

  private fun readableSubjectMasks(
    subjects: List<Subject>,
    token: SegmentationRequestToken
  ): List<ReadableSubjectMask> {
    val result = ArrayList<ReadableSubjectMask>(subjects.size)
    subjects.forEach { subject ->
      token.throwIfCancelled()
      val width = subject.width
      val height = subject.height
      if (width <= 0 || height <= 0 || width > Int.MAX_VALUE / height) {
        return@forEach
      }
      val buffer = subject.confidenceMask ?: return@forEach
      val expectedCount = width * height
      val source = readableBuffer(buffer, expectedCount) ?: return@forEach
      val confidence = try {
        FloatArray(expectedCount)
      } catch (error: OutOfMemoryError) {
        throw segmentationError(
          "ERR_SUBJECT_SEGMENTATION_MASK_ACCESS",
          "The ML Kit confidence mask could not be copied into memory.",
          error
        )
      }
      for (index in 0 until expectedCount) {
        if (index % 65_536 == 0) token.throwIfCancelled()
        val value = source.get(index)
        confidence[index] = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
      }
      result.add(
        ReadableSubjectMask(
          subject.startX,
          subject.startY,
          width,
          height,
          confidence
        )
      )
    }
    return result
  }

  private fun readableBuffer(buffer: FloatBuffer, expectedCount: Int): FloatBuffer? {
    val duplicate = buffer.duplicate()
    duplicate.rewind()
    return if (duplicate.remaining() >= expectedCount) duplicate else null
  }

  private fun subjectAt(
    subjects: List<ReadableSubjectMask>,
    x: Int,
    y: Int
  ): ReadableSubjectMask? = subjects
    .asSequence()
    .mapNotNull { subject ->
      val localX = x - subject.startX
      val localY = y - subject.startY
      if (localX !in 0 until subject.width || localY !in 0 until subject.height) {
        null
      } else {
        val confidence = subject.confidence[localY * subject.width + localX]
        if (confidence >= 0.5f) subject to confidence else null
      }
    }
    .maxByOrNull { it.second }
    ?.first

  private fun nearestSubject(
    subjects: List<ReadableSubjectMask>,
    targetX: Int,
    targetY: Int,
    threshold: Float,
    token: SegmentationRequestToken
  ): ReadableSubjectMask? {
    var nearest: ReadableSubjectMask? = null
    var nearestDistanceSquared = Double.MAX_VALUE
    subjects.forEach { subject ->
      for (localY in 0 until subject.height) {
        if (localY % 64 == 0) token.throwIfCancelled()
        val frameY = subject.startY + localY
        val dy = (frameY - targetY).toDouble()
        val dySquared = dy * dy
        val rowOffset = localY * subject.width
        for (localX in 0 until subject.width) {
          val confidence = subject.confidence[rowOffset + localX]
          val isForeground = if (threshold > 0f) confidence >= threshold else confidence > 0f
          if (!isForeground) continue
          val frameX = subject.startX + localX
          val dx = (frameX - targetX).toDouble()
          val distanceSquared = dx * dx + dySquared
          if (distanceSquared < nearestDistanceSquared) {
            nearestDistanceSquared = distanceSquared
            nearest = subject
          }
        }
      }
    }
    return nearest
  }

  private fun fullFrameMask(
    subject: ReadableSubjectMask,
    frameWidth: Int,
    frameHeight: Int,
    token: SegmentationRequestToken
  ): ByteArray {
    val pixelCount = frameWidth.toLong() * frameHeight.toLong()
    if (frameWidth <= 0 || frameHeight <= 0 || pixelCount > Int.MAX_VALUE) {
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_MASK_FORMAT",
        "ML Kit returned invalid foreground mask dimensions."
      )
    }
    val luminance = try {
      ByteArray(pixelCount.toInt())
    } catch (error: OutOfMemoryError) {
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_MASK_RENDER",
        "The full-frame foreground mask could not be allocated.",
        error
      )
    }

    for (localY in 0 until subject.height) {
      if (localY % 64 == 0) token.throwIfCancelled()
      val frameY = subject.startY + localY
      if (frameY !in 0 until frameHeight) continue
      val sourceRow = localY * subject.width
      val destinationRow = frameY * frameWidth
      for (localX in 0 until subject.width) {
        val frameX = subject.startX + localX
        if (frameX !in 0 until frameWidth) continue
        luminance[destinationRow + frameX] =
          (subject.confidence[sourceRow + localX] * 255f).roundToInt().coerceIn(0, 255).toByte()
      }
    }
    return luminance
  }

  private fun normalizedSelection(value: Double?): Double = when {
    value == null || !value.isFinite() -> 0.5
    else -> value.coerceIn(0.0, 1.0)
  }

  private fun mlKitError(action: String, error: Throwable): CodedException {
    val mlKitException = error as? MlKitException
    val modelUnavailable = mlKitException?.errorCode in setOf(
      MlKitException.FAILED_PRECONDITION,
      MlKitException.UNAVAILABLE,
      MlKitException.NETWORK_ISSUE,
      MlKitException.UNSUPPORTED,
      MlKitException.MODEL_INCOMPATIBLE_WITH_TFLITE,
      MlKitException.NOT_ENOUGH_SPACE,
      MlKitException.MODEL_HASH_MISMATCH
    )
    val message = if (modelUnavailable) {
      "The ML Kit subject segmentation model is unavailable. Wait for its Google Play services download to finish, then try again. If the error persists, this device may not support the model."
    } else {
      "ML Kit could not $action${mlKitException?.let { " (error ${it.errorCode})" }.orEmpty()}."
    }
    return segmentationError("ERR_SUBJECT_SEGMENTATION_VISION", message, error)
  }

  companion object {
    private val DIRECT_EXECUTOR = Executor { runnable -> runnable.run() }
  }
}
