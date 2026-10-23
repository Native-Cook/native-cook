// Adapted from the original renderer/segmentation core without Expo.
package com.ngocdevv.nativecook.blocks.animations.particledispersion

import kotlinx.coroutines.CancellationException
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

internal data class NormalizedPoint(
  val x: Double,
  val y: Double
) {
  fun asMap(): Map<String, Double> = mapOf("x" to x, "y" to y)
}

internal data class NormalizedBounds(
  val x: Double,
  val y: Double,
  val width: Double,
  val height: Double
) {
  val maxX: Double
    get() = x + width

  fun asMap(): Map<String, Double> = mapOf(
    "x" to x,
    "y" to y,
    "width" to width,
    "height" to height
  )
}

internal data class MaskGeometryResult(
  val bounds: NormalizedBounds,
  val contour: List<NormalizedPoint>
)

private data class PixelBounds(
  val minX: Int,
  val minY: Int,
  val maxX: Int,
  val maxY: Int,
  val threshold: Int
)

internal object MaskGeometry {
  fun analyze(
    luminance: ByteArray,
    width: Int,
    height: Int,
    token: SegmentationRequestToken
  ): MaskGeometryResult {
    if (width <= 0 || height <= 0 || luminance.size != width * height) {
      throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_MASK_FORMAT",
        "The foreground mask dimensions do not match its pixel data."
      )
    }

    val detectedBounds = pixelBounds(luminance, width, height, 128, token)
      ?: pixelBounds(luminance, width, height, 1, token)
      ?: throw segmentationError(
        "ERR_SUBJECT_SEGMENTATION_MASK_EMPTY",
        "ML Kit returned a mask without foreground pixels."
      )
    val normalizedBounds = NormalizedBounds(
      x = detectedBounds.minX.toDouble() / width,
      y = detectedBounds.minY.toDouble() / height,
      width = (detectedBounds.maxX - detectedBounds.minX + 1).toDouble() / width,
      height = (detectedBounds.maxY - detectedBounds.minY + 1).toDouble() / height
    )

    val contour = try {
      exteriorEnvelope(
        luminance,
        width,
        height,
        detectedBounds,
        normalizedBounds,
        token
      )
    } catch (error: CancellationException) {
      throw error
    } catch (_: Throwable) {
      // Contour metadata is additive; a valid foreground mask remains usable.
      emptyList()
    }

    return MaskGeometryResult(normalizedBounds, contour)
  }

  private fun pixelBounds(
    luminance: ByteArray,
    width: Int,
    height: Int,
    threshold: Int,
    token: SegmentationRequestToken
  ): PixelBounds? {
    var minX = width
    var minY = height
    var maxX = -1
    var maxY = -1

    for (y in 0 until height) {
      if (y % 64 == 0) token.throwIfCancelled()
      val rowOffset = y * width
      for (x in 0 until width) {
        if ((luminance[rowOffset + x].toInt() and 0xff) < threshold) continue
        minX = min(minX, x)
        minY = min(minY, y)
        maxX = max(maxX, x)
        maxY = max(maxY, y)
      }
    }

    return if (maxX >= minX && maxY >= minY) {
      PixelBounds(minX, minY, maxX, maxY, threshold)
    } else {
      null
    }
  }

  /**
   * Builds an ordered exterior envelope from row and column extrema. It is a
   * stable approximation for soft masks, avoids platform contour dependencies,
   * and is uniformly resampled for scene metadata.
   */
  private fun exteriorEnvelope(
    luminance: ByteArray,
    width: Int,
    height: Int,
    bounds: PixelBounds,
    normalizedBounds: NormalizedBounds,
    token: SegmentationRequestToken
  ): List<NormalizedPoint> {
    val top = IntArray(width) { -1 }
    val bottom = IntArray(width) { -1 }
    val left = IntArray(height) { -1 }
    val right = IntArray(height) { -1 }

    for (y in bounds.minY..bounds.maxY) {
      if (y % 64 == 0) token.throwIfCancelled()
      val rowOffset = y * width
      for (x in bounds.minX..bounds.maxX) {
        if ((luminance[rowOffset + x].toInt() and 0xff) < bounds.threshold) continue
        if (top[x] == -1) top[x] = y
        bottom[x] = y
        if (left[y] == -1) left[y] = x
        right[y] = x
      }
    }

    val points = ArrayList<NormalizedPoint>(
      2 * (bounds.maxX - bounds.minX + bounds.maxY - bounds.minY + 2)
    )
    for (x in bounds.minX..bounds.maxX) {
      if (top[x] >= 0) points.add(normalizedPoint(x, top[x], width, height))
    }
    for (y in bounds.minY..bounds.maxY) {
      if (right[y] >= 0) points.add(normalizedPoint(right[y], y, width, height))
    }
    for (x in bounds.maxX downTo bounds.minX) {
      if (bottom[x] >= 0) points.add(normalizedPoint(x, bottom[x], width, height))
    }
    for (y in bounds.maxY downTo bounds.minY) {
      if (left[y] >= 0) points.add(normalizedPoint(left[y], y, width, height))
    }

    val uniquePoints = removeConsecutiveDuplicates(points)
    if (uniquePoints.size < 3) return emptyList()
    val resampled = resampleClosedContour(uniquePoints, width, height, 250)
    return orderFromTopRightAcrossTop(resampled, normalizedBounds, width, height)
  }

  private fun normalizedPoint(x: Int, y: Int, width: Int, height: Int) = NormalizedPoint(
    x = (x.toDouble() / width).coerceIn(0.0, 1.0),
    y = (y.toDouble() / height).coerceIn(0.0, 1.0)
  )

  private fun removeConsecutiveDuplicates(
    points: List<NormalizedPoint>
  ): List<NormalizedPoint> {
    val result = ArrayList<NormalizedPoint>(points.size)
    points.forEach { point ->
      val previous = result.lastOrNull()
      if (previous == null || squaredDistance(previous, point) > 1e-12) {
        result.add(point)
      }
    }
    if (
      result.size > 1 &&
      squaredDistance(result.first(), result.last()) <= 1e-12
    ) {
      result.removeAt(result.lastIndex)
    }
    return result
  }

  private fun resampleClosedContour(
    points: List<NormalizedPoint>,
    pixelWidth: Int,
    pixelHeight: Int,
    maximumPointCount: Int
  ): List<NormalizedPoint> {
    if (points.size < 3 || maximumPointCount < 3) return points
    val sampleCount = max(min(64, maximumPointCount), min(points.size, maximumPointCount))
    val segmentLengths = DoubleArray(points.size)
    var totalLength = 0.0
    for (index in points.indices) {
      val length = pixelDistance(
        points[index],
        points[(index + 1) % points.size],
        pixelWidth,
        pixelHeight
      )
      segmentLengths[index] = length
      totalLength += length
    }
    if (totalLength <= 0.0 || !totalLength.isFinite()) {
      return points.take(sampleCount)
    }

    val spacing = totalLength / sampleCount
    val result = ArrayList<NormalizedPoint>(sampleCount)
    var segmentIndex = 0
    var segmentStartDistance = 0.0
    for (sampleIndex in 0 until sampleCount) {
      val targetDistance = sampleIndex * spacing
      while (
        segmentIndex < segmentLengths.lastIndex &&
        segmentStartDistance + segmentLengths[segmentIndex] < targetDistance
      ) {
        segmentStartDistance += segmentLengths[segmentIndex]
        segmentIndex += 1
      }
      val start = points[segmentIndex]
      val end = points[(segmentIndex + 1) % points.size]
      val segmentLength = segmentLengths[segmentIndex]
      val progress = if (segmentLength > 0) {
        (targetDistance - segmentStartDistance) / segmentLength
      } else {
        0.0
      }
      result.add(
        NormalizedPoint(
          x = start.x + (end.x - start.x) * progress,
          y = start.y + (end.y - start.y) * progress
        )
      )
    }
    return result
  }

  private fun orderFromTopRightAcrossTop(
    points: List<NormalizedPoint>,
    bounds: NormalizedBounds,
    pixelWidth: Int,
    pixelHeight: Int
  ): List<NormalizedPoint> {
    if (points.size < 3) return points
    val topBandMaximumY = bounds.y + max(bounds.height * 0.28, 2.0 / max(pixelHeight, 1))
    val topBandIndices = points.indices.filter { points[it].y <= topBandMaximumY }
    val candidates = topBandIndices.ifEmpty { points.indices.toList() }
    val horizontalScale = max(bounds.width, 1.0 / max(pixelWidth, 1))
    val verticalScale = max(bounds.height, 1.0 / max(pixelHeight, 1))

    fun cornerDistance(index: Int, targetX: Double): Double {
      val dx = (points[index].x - targetX) / horizontalScale
      val dy = (points[index].y - bounds.y) / verticalScale
      return dx * dx + dy * dy
    }

    val startIndex = candidates.minByOrNull { cornerDistance(it, bounds.maxX) } ?: return points
    val topLeftIndex = candidates.minByOrNull { cornerDistance(it, bounds.x) } ?: return points
    val forwardDistance = contourDistance(
      points,
      startIndex,
      topLeftIndex,
      1,
      pixelWidth,
      pixelHeight
    )
    val reverseDistance = contourDistance(
      points,
      startIndex,
      topLeftIndex,
      -1,
      pixelWidth,
      pixelHeight
    )
    val step = if (forwardDistance <= reverseDistance) 1 else -1

    return List(points.size) { offset ->
      val index = Math.floorMod(startIndex + step * offset, points.size)
      points[index]
    }
  }

  private fun contourDistance(
    points: List<NormalizedPoint>,
    startIndex: Int,
    targetIndex: Int,
    step: Int,
    pixelWidth: Int,
    pixelHeight: Int
  ): Double {
    if (startIndex == targetIndex) return 0.0
    var distance = 0.0
    var currentIndex = startIndex
    repeat(points.size) {
      val nextIndex = Math.floorMod(currentIndex + step, points.size)
      distance += pixelDistance(
        points[currentIndex],
        points[nextIndex],
        pixelWidth,
        pixelHeight
      )
      if (nextIndex == targetIndex) return distance
      currentIndex = nextIndex
    }
    return Double.MAX_VALUE
  }

  private fun pixelDistance(
    start: NormalizedPoint,
    end: NormalizedPoint,
    pixelWidth: Int,
    pixelHeight: Int
  ): Double = hypot(
    (end.x - start.x) * pixelWidth,
    (end.y - start.y) * pixelHeight
  )

  private fun squaredDistance(start: NormalizedPoint, end: NormalizedPoint): Double {
    val dx = end.x - start.x
    val dy = end.y - start.y
    return dx * dx + dy * dy
  }
}
