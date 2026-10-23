// Adapted from the original renderer/segmentation core without Expo.
package com.ngocdevv.nativecook.blocks.animations.particledispersion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.roundToInt

internal class ImageFrameProcessor(context: Context) {
  private val outputDirectory = File(context.cacheDir, "SubjectSegmentation")

  fun prepare(
    options: PrepareEffectFrameOptions,
    token: SegmentationRequestToken
  ): Map<String, Any?> {
    validateTargetDimensions(options.targetWidth, options.targetHeight)
    token.throwIfCancelled()

    val source = localFile(options.sourceUri)
    if (!source.isFile || !source.canRead()) {
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_SOURCE_UNREADABLE",
        "The captured image is not readable at '${source.path}'."
      )
    }

    val orientation = readOrientation(source)
    val decodeOptions = bitmapDecodeOptions(
      source,
      orientation,
      options.targetWidth,
      options.targetHeight
    )
    token.throwIfCancelled()

    var workingBitmap = try {
      BitmapFactory.decodeFile(source.path, decodeOptions)
    } catch (error: OutOfMemoryError) {
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_IMAGE_DECODE",
        "The captured image is too large to decode.",
        error
      )
    } ?: throw segmentationError(
      "ERR_SUBJECT_SEGMENTATION_IMAGE_DECODE",
      "The captured image could not be decoded."
    )

    try {
      token.throwIfCancelled()

      val uprightBitmap = applyExifOrientation(workingBitmap, orientation)
      if (uprightBitmap !== workingBitmap) {
        workingBitmap.recycle()
        workingBitmap = uprightBitmap
      }
      if (workingBitmap.width <= 0 || workingBitmap.height <= 0) {
        throw segmentationError(
          "ERR_SUBJECT_SEGMENTATION_IMAGE_EMPTY",
          "The captured image has invalid dimensions."
        )
      }

      token.throwIfCancelled()
      val croppedBitmap = centerCrop(
        workingBitmap,
        options.targetWidth,
        options.targetHeight
      )
      if (croppedBitmap !== workingBitmap) {
        workingBitmap.recycle()
        workingBitmap = croppedBitmap
      }

      val scaledBitmap = try {
        Bitmap.createScaledBitmap(
          workingBitmap,
          options.targetWidth,
          options.targetHeight,
          true
        )
      } catch (error: Throwable) {
        if (error is NativeProcessingException) throw error
        throw segmentationError(
          "ERR_SUBJECT_SEGMENTATION_IMAGE_RENDER",
          "The canonical effect frame could not be rendered.",
          error
        )
      }
      if (scaledBitmap !== workingBitmap) {
        workingBitmap.recycle()
        workingBitmap = scaledBitmap
      }

      token.throwIfCancelled()
      val output = makeOutputFile("jpg")
      try {
        writeBitmap(workingBitmap, output, Bitmap.CompressFormat.JPEG, 95)
        token.throwIfCancelled()
      } catch (error: Throwable) {
        output.delete()
        throw error
      }

      return mapOf(
        "requestId" to options.requestId,
        "frameUri" to Uri.fromFile(output).toString(),
        "width" to options.targetWidth,
        "height" to options.targetHeight
      )
    } catch (error: NativeProcessingException) {
      throw error
    } catch (error: SegmentationCancelledException) {
      throw error
    } catch (error: OutOfMemoryError) {
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_IMAGE_RENDER",
        "The canonical effect frame could not be rendered because memory was exhausted.",
        error
      )
    } catch (error: Throwable) {
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_IMAGE_RENDER",
        "The canonical effect frame could not be rendered.",
        error
      )
    } finally {
      if (!workingBitmap.isRecycled) {
        workingBitmap.recycle()
      }
    }
  }

  fun writeMask(
    luminance: ByteArray,
    width: Int,
    height: Int,
    token: SegmentationRequestToken
  ): File {
    if (width <= 0 || height <= 0 || luminance.size != width * height) {
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_MASK_FORMAT",
        "The foreground mask dimensions do not match its pixel data."
      )
    }

    val colors = try {
      IntArray(luminance.size) { index ->
        val value = luminance[index].toInt() and 0xff
        (0xff shl 24) or (value shl 16) or (value shl 8) or value
      }
    } catch (error: OutOfMemoryError) {
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_MASK_RENDER",
        "The grayscale foreground mask could not be allocated.",
        error
      )
    }

    token.throwIfCancelled()
    val bitmap = try {
      Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888)
    } catch (error: Throwable) {
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_MASK_RENDER",
        "The grayscale foreground mask could not be created.",
        error
      )
    }

    val output = makeOutputFile("png")
    try {
      token.throwIfCancelled()
      writeBitmap(bitmap, output, Bitmap.CompressFormat.PNG, 100)
      token.throwIfCancelled()
      return output
    } catch (error: Throwable) {
      output.delete()
      throw error
    } finally {
      bitmap.recycle()
    }
  }

  fun releaseOwnedFiles(uris: List<String>) {
    val root = try {
      outputDirectory.canonicalFile
    } catch (error: IOException) {
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_RELEASE",
        "The subject segmentation cache directory could not be resolved.",
        error
      )
    }
    val rootPrefix = root.path + File.separator

    for (uri in uris) {
      val file = try {
        localFile(uri)
      } catch (_: Throwable) {
        continue
      }
      val canonicalFile = try {
        file.canonicalFile
      } catch (_: IOException) {
        continue
      }
      if (!canonicalFile.path.startsWith(rootPrefix) || !canonicalFile.exists()) {
        continue
      }
      if (!canonicalFile.delete()) {
        throw segmentationError(
          "ERR_SUBJECT_SEGMENTATION_RELEASE",
          "Could not release cached file '${canonicalFile.name}'."
        )
      }
    }
  }

  fun localFile(uriOrPath: String): File {
    val trimmed = uriOrPath.trim()
    if (trimmed.isEmpty()) {
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_URI",
        "A non-empty local file URI is required."
      )
    }

    val file = if (trimmed.startsWith("file://", ignoreCase = true)) {
      val uri = try {
        Uri.parse(trimmed)
      } catch (error: Throwable) {
        throw segmentationError(
          "ERR_SUBJECT_SEGMENTATION_URI",
          "The provided file URI is invalid.",
          error
        )
      }
      val authority = uri.authority
      if (
        !uri.scheme.equals("file", ignoreCase = true) ||
        (!authority.isNullOrEmpty() && !authority.equals("localhost", ignoreCase = true))
      ) {
        throw segmentationError(
          "ERR_SUBJECT_SEGMENTATION_URI",
          "Only local file:// URIs are supported."
        )
      }
      val path = uri.path
      if (path.isNullOrEmpty()) {
        throw segmentationError(
          "ERR_SUBJECT_SEGMENTATION_URI",
          "The provided file URI does not contain an absolute path."
        )
      }
      File(path)
    } else {
      if (!trimmed.startsWith(File.separator)) {
        throw segmentationError(
          "ERR_SUBJECT_SEGMENTATION_URI",
          "Only file:// URIs and absolute local file paths are supported."
        )
      }
      File(trimmed)
    }

    if (!file.isAbsolute) {
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_URI",
        "The provided local file path must be absolute."
      )
    }

    return try {
      file.canonicalFile
    } catch (error: IOException) {
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_URI",
        "The provided local file path could not be resolved.",
        error
      )
    }
  }

  private fun validateTargetDimensions(width: Int, height: Int) {
    val maximumDimension = 4_096
    val maximumPixelCount = 16_777_216
    if (
      width <= 0 ||
      height <= 0 ||
      width > maximumDimension ||
      height > maximumDimension ||
      width > maximumPixelCount / height
    ) {
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_TARGET_SIZE",
        "targetWidth and targetHeight must be positive, at most 4096, and no more than 16 megapixels in total."
      )
    }
  }

  private fun readOrientation(source: File): Int = try {
    ExifInterface(source.path).getAttributeInt(
      ExifInterface.TAG_ORIENTATION,
      ExifInterface.ORIENTATION_UNDEFINED
    )
  } catch (_: IOException) {
    ExifInterface.ORIENTATION_UNDEFINED
  }

  private fun bitmapDecodeOptions(
    source: File,
    orientation: Int,
    targetWidth: Int,
    targetHeight: Int
  ): BitmapFactory.Options {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(source.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_IMAGE_DECODE",
        "The captured image could not be decoded."
      )
    }

    val swapsDimensions = orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
      orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
      orientation == ExifInterface.ORIENTATION_TRANSVERSE ||
      orientation == ExifInterface.ORIENTATION_ROTATE_270
    val uprightWidth = if (swapsDimensions) bounds.outHeight else bounds.outWidth
    val uprightHeight = if (swapsDimensions) bounds.outWidth else bounds.outHeight
    val maximumSample = minOf(
      uprightWidth.toDouble() / targetWidth.toDouble(),
      uprightHeight.toDouble() / targetHeight.toDouble()
    )
    var sampleSize = 1
    while (sampleSize * 2 <= maximumSample) {
      sampleSize *= 2
    }

    return BitmapFactory.Options().apply {
      inPreferredConfig = Bitmap.Config.ARGB_8888
      inSampleSize = sampleSize
    }
  }

  private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
      ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
      ExifInterface.ORIENTATION_TRANSPOSE -> {
        matrix.setRotate(90f)
        matrix.postScale(-1f, 1f)
      }
      ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
      ExifInterface.ORIENTATION_TRANSVERSE -> {
        matrix.setRotate(-90f)
        matrix.postScale(-1f, 1f)
      }
      ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
      else -> return bitmap
    }

    return try {
      Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } catch (error: Throwable) {
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_IMAGE_RENDER",
        "The captured image orientation could not be applied.",
        error
      )
    }
  }

  private fun centerCrop(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
    val targetAspect = targetWidth.toDouble() / targetHeight.toDouble()
    val sourceAspect = bitmap.width.toDouble() / bitmap.height.toDouble()
    val cropWidth: Int
    val cropHeight: Int

    if (sourceAspect > targetAspect) {
      cropWidth = (bitmap.height * targetAspect).roundToInt().coerceIn(1, bitmap.width)
      cropHeight = bitmap.height
    } else {
      cropWidth = bitmap.width
      cropHeight = (bitmap.width / targetAspect).roundToInt().coerceIn(1, bitmap.height)
    }

    val cropX = (bitmap.width - cropWidth) / 2
    val cropY = (bitmap.height - cropHeight) / 2
    return try {
      Bitmap.createBitmap(bitmap, cropX, cropY, cropWidth, cropHeight)
    } catch (error: Throwable) {
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_IMAGE_RENDER",
        "The captured image could not be cropped to the requested aspect ratio.",
        error
      )
    }
  }

  private fun makeOutputFile(extension: String): File {
    try {
      if (!outputDirectory.isDirectory && !outputDirectory.mkdirs()) {
        throw IOException("mkdirs returned false")
      }
      return File(outputDirectory.canonicalFile, "${UUID.randomUUID()}.$extension")
    } catch (error: Throwable) {
      if (error is NativeProcessingException) throw error
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_OUTPUT_DIRECTORY",
        "The subject segmentation cache directory could not be created.",
        error
      )
    }
  }

  private fun writeBitmap(
    bitmap: Bitmap,
    output: File,
    format: Bitmap.CompressFormat,
    quality: Int
  ) {
    try {
      FileOutputStream(output).use { stream ->
        if (!bitmap.compress(format, quality, stream)) {
          throw IOException("Bitmap encoder returned false")
        }
        stream.flush()
      }
    } catch (error: Throwable) {
      if (error is NativeProcessingException) throw error
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_IMAGE_WRITE",
        "The image encoder could not write '${output.name}'.",
        error
      )
    }
  }
}
