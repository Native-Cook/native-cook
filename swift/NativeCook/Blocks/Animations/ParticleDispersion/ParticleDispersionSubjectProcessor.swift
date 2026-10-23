import CoreGraphics
import CoreImage
import CoreVideo
import Foundation
import ImageIO
import UniformTypeIdentifiers
import Vision

struct ParticleDispersionPrepareEffectFrameOptions {
  var sourceUri: String = ""
  var requestId: String = ""
  var targetWidth: Int = 0
  var targetHeight: Int = 0
}

struct ParticleDispersionSegmentEffectFrameOptions {
  var frameUri: String = ""
  var requestId: String = ""
  var selectionX: Double?
  var selectionY: Double?
}

private struct ParticleDispersionGrayscaleMask {
  let image: CGImage
  let luminance: [UInt8]
  let width: Int
  let height: Int
}

private final class ParticleDispersionSegmentationRequestRegistry {
  private let lock = NSLock()
  private var cancelledRequestIds = Set<String>()
  private var activeRequests: [String: VNRequest] = [:]

  func cancel(_ requestId: String) {
    let request: VNRequest?

    lock.lock()
    cancelledRequestIds.insert(requestId)
    request = activeRequests[requestId]
    lock.unlock()

    request?.cancel()
  }

  func register(_ request: VNRequest, for requestId: String) throws {
    let wasCancelled: Bool

    lock.lock()
    activeRequests[requestId] = request
    wasCancelled = cancelledRequestIds.contains(requestId)
    lock.unlock()

    if wasCancelled {
      request.cancel()
      unregister(request, for: requestId)
      throw segmentationError(
        code: "ERR_SUBJECT_SEGMENTATION_CANCELLED",
        description: "Subject segmentation request '\(requestId)' was cancelled."
      )
    }
  }

  func unregister(_ request: VNRequest, for requestId: String) {
    lock.lock()
    if activeRequests[requestId] === request {
      activeRequests.removeValue(forKey: requestId)
    }
    lock.unlock()
  }

  func throwIfCancelled(_ requestId: String) throws {
    lock.lock()
    let isCancelled = cancelledRequestIds.contains(requestId)
    lock.unlock()

    if isCancelled {
      throw segmentationError(
        code: "ERR_SUBJECT_SEGMENTATION_CANCELLED",
        description: "Subject segmentation request '\(requestId)' was cancelled."
      )
    }
  }

  func isCancelled(_ requestId: String) -> Bool {
    lock.lock()
    let result = cancelledRequestIds.contains(requestId)
    lock.unlock()
    return result
  }
}

// Adapted from the original native module; this target has no Expo dependency.
final class ParticleDispersionSubjectProcessor {
  private let imageContext = CIContext(options: [.cacheIntermediates: false, .name: "SubjectSegmentation"])
  private let requestRegistry = ParticleDispersionSegmentationRequestRegistry()
  func cancel(_ requestId: String) { requestRegistry.cancel(requestId) }
  func prepareEffectFrame(_ options: ParticleDispersionPrepareEffectFrameOptions) throws -> [String: Any] {
    try validateTargetDimensions(width: options.targetWidth, height: options.targetHeight)

    let sourceURL = try localFileURL(from: options.sourceUri)
    guard FileManager.default.isReadableFile(atPath: sourceURL.path) else {
      throw segmentationError(
        code: "ERR_SUBJECT_SEGMENTATION_SOURCE_UNREADABLE",
        description: "The captured image is not readable at '\(sourceURL.path)'."
      )
    }

    guard let loadedImage = CIImage(
      contentsOf: sourceURL,
      options: [
        .applyOrientationProperty: true,
        .cacheImmediately: true
      ]
    ) else {
      throw segmentationError(
        code: "ERR_SUBJECT_SEGMENTATION_IMAGE_DECODE",
        description: "The captured image could not be decoded."
      )
    }

    try requestRegistry.throwIfCancelled(options.requestId)

    let uprightImage = normalizedToZeroOrigin(loadedImage)
    guard uprightImage.extent.width > 0, uprightImage.extent.height > 0 else {
      throw segmentationError(
        code: "ERR_SUBJECT_SEGMENTATION_IMAGE_EMPTY",
        description: "The captured image has invalid dimensions."
      )
    }

    let cropRect = centerCropRect(
      sourceExtent: uprightImage.extent,
      targetWidth: options.targetWidth,
      targetHeight: options.targetHeight
    )
    let croppedImage = uprightImage
      .cropped(to: cropRect)
      .transformed(by: CGAffineTransform(
        translationX: -cropRect.minX,
        y: -cropRect.minY
      ))

    let scale = CGFloat(options.targetWidth) / cropRect.width
    let scaledImage = croppedImage.applyingFilter(
      "CILanczosScaleTransform",
      parameters: [
        kCIInputScaleKey: scale,
        kCIInputAspectRatioKey: 1.0
      ]
    )
    let outputBounds = CGRect(
      x: 0,
      y: 0,
      width: options.targetWidth,
      height: options.targetHeight
    )
    let colorSpace = CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB()

    guard let outputImage = imageContext.createCGImage(
      scaledImage,
      from: outputBounds,
      format: .RGBA8,
      colorSpace: colorSpace
    ) else {
      throw segmentationError(
        code: "ERR_SUBJECT_SEGMENTATION_IMAGE_RENDER",
        description: "The canonical effect frame could not be rendered."
      )
    }

    try requestRegistry.throwIfCancelled(options.requestId)

    let outputURL = try makeOutputURL(pathExtension: "jpg")
    do {
      try writeImage(
        outputImage,
        to: outputURL,
        type: UTType.jpeg.identifier as CFString,
        properties: [kCGImageDestinationLossyCompressionQuality: 0.95]
      )
      try requestRegistry.throwIfCancelled(options.requestId)
    } catch {
      try? FileManager.default.removeItem(at: outputURL)
      throw error
    }

    return [
      "requestId": options.requestId,
      "frameUri": outputURL.absoluteString,
      "width": options.targetWidth,
      "height": options.targetHeight
    ]
  }

  func segmentEffectFrame(_ options: ParticleDispersionSegmentEffectFrameOptions) throws -> [String: Any] {
    let frameURL = try localFileURL(from: options.frameUri)
    guard FileManager.default.isReadableFile(atPath: frameURL.path) else {
      throw segmentationError(
        code: "ERR_SUBJECT_SEGMENTATION_FRAME_UNREADABLE",
        description: "The prepared effect frame is not readable at '\(frameURL.path)'."
      )
    }

    guard
      let imageSource = CGImageSourceCreateWithURL(frameURL as CFURL, nil),
      let frameImage = CGImageSourceCreateImageAtIndex(imageSource, 0, [
        kCGImageSourceShouldCacheImmediately: true
      ] as CFDictionary)
    else {
      throw segmentationError(
        code: "ERR_SUBJECT_SEGMENTATION_IMAGE_DECODE",
        description: "The prepared effect frame could not be decoded."
      )
    }

    try requestRegistry.throwIfCancelled(options.requestId)

    let request = VNGenerateForegroundInstanceMaskRequest()
    let requestHandler = VNImageRequestHandler(
      cgImage: frameImage,
      orientation: .up,
      options: [:]
    )

    try requestRegistry.register(request, for: options.requestId)
    defer {
      requestRegistry.unregister(request, for: options.requestId)
    }

    do {
      try requestHandler.perform([request])
    } catch {
      if requestRegistry.isCancelled(options.requestId) {
        throw segmentationError(
          code: "ERR_SUBJECT_SEGMENTATION_CANCELLED",
          description: "Subject segmentation request '\(options.requestId)' was cancelled.",
          cause: error
        )
      }

      throw segmentationError(
        code: "ERR_SUBJECT_SEGMENTATION_VISION",
        description: "Apple Vision could not generate a foreground instance mask.",
        cause: error
      )
    }

    try requestRegistry.throwIfCancelled(options.requestId)

    guard let observation = request.results?.first else {
      throw segmentationError(
        code: "ERR_SUBJECT_SEGMENTATION_NO_SUBJECT",
        description: "Apple Vision did not find a separable foreground subject."
      )
    }

    let allInstances = observation.allInstances
    guard let firstInstanceIdentifier = allInstances.first else {
      throw segmentationError(
        code: "ERR_SUBJECT_SEGMENTATION_NO_SUBJECT",
        description: "Apple Vision did not find a separable foreground subject."
      )
    }

    let selection = CGPoint(
      x: min(max(options.selectionX ?? 0.5, 0), 1),
      y: min(max(options.selectionY ?? 0.5, 0), 1)
    )
    let selectedIdentifier = instanceIdentifier(
      at: selection,
      in: observation.instanceMask
    )

    let selectedInstances: IndexSet
    let selectionFallback: Bool
    if
      let selectedIdentifier,
      selectedIdentifier != 0,
      allInstances.contains(selectedIdentifier)
    {
      selectedInstances = IndexSet(integer: selectedIdentifier)
      selectionFallback = false
    } else {
      let nearestIdentifier = try nearestInstanceIdentifier(
        to: selection,
        in: observation.instanceMask,
        allowedIdentifiers: allInstances,
        requestId: options.requestId
      )
      selectedInstances = IndexSet(integer: nearestIdentifier ?? firstInstanceIdentifier)
      selectionFallback = true
    }

    let scaledMask: CVPixelBuffer
    do {
      scaledMask = try observation.generateScaledMaskForImage(
        forInstances: selectedInstances,
        from: requestHandler
      )
    } catch {
      if requestRegistry.isCancelled(options.requestId) {
        throw segmentationError(
          code: "ERR_SUBJECT_SEGMENTATION_CANCELLED",
          description: "Subject segmentation request '\(options.requestId)' was cancelled.",
          cause: error
        )
      }

      throw segmentationError(
        code: "ERR_SUBJECT_SEGMENTATION_MASK_RENDER",
        description: "Apple Vision could not scale the selected subject mask to the effect frame.",
        cause: error
      )
    }

    try requestRegistry.throwIfCancelled(options.requestId)

    let mask = try grayscaleMask(
      from: scaledMask,
      requestId: options.requestId
    )
    let subjectBounds = try normalizedSubjectBounds(
      in: mask,
      requestId: options.requestId
    )
    let contourPoints: [CGPoint]
    do {
      contourPoints = try exteriorContourPoints(
        from: mask.image,
        subjectBounds: subjectBounds,
        requestId: options.requestId
      )
    } catch {
      if requestRegistry.isCancelled(options.requestId) {
        throw segmentationError(
          code: "ERR_SUBJECT_SEGMENTATION_CANCELLED",
          description: "Subject segmentation request '\(options.requestId)' was cancelled.",
          cause: error
        )
      }

      // Contour metadata is additive. A contour-detection failure must not make an
      // otherwise valid foreground mask unusable.
      contourPoints = []
    }

    let outputURL = try makeOutputURL(pathExtension: "png")

    do {
      try writeImage(
        mask.image,
        to: outputURL,
        type: UTType.png.identifier as CFString,
        properties: nil
      )
      try requestRegistry.throwIfCancelled(options.requestId)
    } catch {
      try? FileManager.default.removeItem(at: outputURL)
      throw error
    }

    return [
      "requestId": options.requestId,
      "maskUri": outputURL.absoluteString,
      "width": mask.width,
      "height": mask.height,
      "selectedInstanceCount": selectedInstances.count,
      "selectionFallback": selectionFallback,
      "subjectBounds": [
        "x": Double(subjectBounds.minX),
        "y": Double(subjectBounds.minY),
        "width": Double(subjectBounds.width),
        "height": Double(subjectBounds.height)
      ],
      "contourPoints": contourPoints.map { point in
        [
          "x": Double(point.x),
          "y": Double(point.y)
        ]
      }
    ]
  }

  private func grayscaleMask(
    from pixelBuffer: CVPixelBuffer,
    requestId: String
  ) throws -> ParticleDispersionGrayscaleMask {
    let width = CVPixelBufferGetWidth(pixelBuffer)
    let height = CVPixelBufferGetHeight(pixelBuffer)
    let bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer)
    let pixelFormat = CVPixelBufferGetPixelFormatType(pixelBuffer)

    guard width > 0, height > 0 else {
      throw segmentationError(
        code: "ERR_SUBJECT_SEGMENTATION_MASK_EMPTY",
        description: "Apple Vision returned an empty foreground mask."
      )
    }

    let lockStatus = CVPixelBufferLockBaseAddress(pixelBuffer, .readOnly)
    guard lockStatus == kCVReturnSuccess else {
      throw segmentationError(
        code: "ERR_SUBJECT_SEGMENTATION_MASK_ACCESS",
        description: "The Apple Vision mask pixel buffer could not be locked."
      )
    }
    defer {
      CVPixelBufferUnlockBaseAddress(pixelBuffer, .readOnly)
    }

    guard let sourceBaseAddress = CVPixelBufferGetBaseAddress(pixelBuffer) else {
      throw segmentationError(
        code: "ERR_SUBJECT_SEGMENTATION_MASK_ACCESS",
        description: "The Apple Vision mask does not expose readable pixels."
      )
    }

    var luminance = [UInt8](repeating: 0, count: width * height)
    try luminance.withUnsafeMutableBufferPointer { destination in
      guard let destinationBaseAddress = destination.baseAddress else {
        throw segmentationError(
          code: "ERR_SUBJECT_SEGMENTATION_MASK_ACCESS",
          description: "Could not allocate storage for the foreground mask."
        )
      }

      for rowIndex in 0..<height {
        if rowIndex.isMultiple(of: 64) {
          try requestRegistry.throwIfCancelled(requestId)
        }

        let sourceRow = sourceBaseAddress.advanced(by: rowIndex * bytesPerRow)
        let destinationRow = destinationBaseAddress.advanced(by: rowIndex * width)

        switch pixelFormat {
        case kCVPixelFormatType_OneComponent8:
          destinationRow.update(
            from: sourceRow.assumingMemoryBound(to: UInt8.self),
            count: width
          )

        case kCVPixelFormatType_OneComponent16Half:
          let source = sourceRow.assumingMemoryBound(to: UInt16.self)
          for columnIndex in 0..<width {
            let value = Float(Float16(bitPattern: source[columnIndex]))
            destinationRow[columnIndex] = maskByte(from: value)
          }

        case kCVPixelFormatType_OneComponent32Float:
          let source = sourceRow.assumingMemoryBound(to: Float.self)
          for columnIndex in 0..<width {
            destinationRow[columnIndex] = maskByte(from: source[columnIndex])
          }

        default:
          throw segmentationError(
            code: "ERR_SUBJECT_SEGMENTATION_MASK_FORMAT",
            description: "Apple Vision returned unsupported mask pixel format \(pixelFormat)."
          )
        }
      }
    }

    let data = Data(luminance)
    guard
      let provider = CGDataProvider(data: data as CFData),
      let image = CGImage(
        width: width,
        height: height,
        bitsPerComponent: 8,
        bitsPerPixel: 8,
        bytesPerRow: width,
        space: CGColorSpaceCreateDeviceGray(),
        bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.none.rawValue),
        provider: provider,
        decode: nil,
        shouldInterpolate: true,
        intent: .defaultIntent
      )
    else {
      throw segmentationError(
        code: "ERR_SUBJECT_SEGMENTATION_MASK_RENDER",
        description: "The grayscale foreground mask could not be created."
      )
    }

    return ParticleDispersionGrayscaleMask(
      image: image,
      luminance: luminance,
      width: width,
      height: height
    )
  }

  private func normalizedSubjectBounds(
    in mask: ParticleDispersionGrayscaleMask,
    requestId: String
  ) throws -> CGRect {
    func pixelBounds(threshold: UInt8) throws -> (
      minX: Int,
      minY: Int,
      maxX: Int,
      maxY: Int
    )? {
      var minX = mask.width
      var minY = mask.height
      var maxX = -1
      var maxY = -1

      for rowIndex in 0..<mask.height {
        if rowIndex.isMultiple(of: 64) {
          try requestRegistry.throwIfCancelled(requestId)
        }

        let rowOffset = rowIndex * mask.width
        for columnIndex in 0..<mask.width
        where mask.luminance[rowOffset + columnIndex] >= threshold {
          minX = min(minX, columnIndex)
          minY = min(minY, rowIndex)
          maxX = max(maxX, columnIndex)
          maxY = max(maxY, rowIndex)
        }
      }

      guard maxX >= minX, maxY >= minY else {
        return nil
      }

      return (minX, minY, maxX, maxY)
    }

    // A half-alpha threshold rejects the soft, low-confidence mask fringe. The
    // non-zero fallback keeps unusually faint but otherwise valid Vision masks usable.
    let detectedBounds: (minX: Int, minY: Int, maxX: Int, maxY: Int)?
    if let confidentBounds = try pixelBounds(threshold: 128) {
      detectedBounds = confidentBounds
    } else {
      detectedBounds = try pixelBounds(threshold: 1)
    }

    guard let bounds = detectedBounds else {
      throw segmentationError(
        code: "ERR_SUBJECT_SEGMENTATION_MASK_EMPTY",
        description: "Apple Vision returned a mask without foreground pixels."
      )
    }

    let width = CGFloat(mask.width)
    let height = CGFloat(mask.height)
    return CGRect(
      x: CGFloat(bounds.minX) / width,
      y: CGFloat(bounds.minY) / height,
      width: CGFloat(bounds.maxX - bounds.minX + 1) / width,
      height: CGFloat(bounds.maxY - bounds.minY + 1) / height
    )
  }

  private func exteriorContourPoints(
    from maskImage: CGImage,
    subjectBounds: CGRect,
    requestId: String
  ) throws -> [CGPoint] {
    let request = VNDetectContoursRequest()
    request.contrastAdjustment = 1
    request.contrastPivot = NSNumber(value: 0.5)
    request.detectsDarkOnLight = false
    request.maximumImageDimension = 512

    let requestHandler = VNImageRequestHandler(
      cgImage: maskImage,
      orientation: .up,
      options: [:]
    )

    try requestRegistry.register(request, for: requestId)
    defer {
      requestRegistry.unregister(request, for: requestId)
    }

    try requestHandler.perform([request])
    try requestRegistry.throwIfCancelled(requestId)

    guard let observation = request.results?.first else {
      return []
    }

    let rankedContours = observation.topLevelContours.map { contour in
      (contour: contour, area: absoluteArea(of: contour))
    }
    guard
      let exteriorContour = rankedContours.max(by: { $0.area < $1.area })?.contour
    else {
      return []
    }

    let simplifiedContour = simplifiedContour(exteriorContour, maximumPointCount: 250)
    var points = simplifiedContour.normalizedPoints.compactMap { value -> CGPoint? in
      let x = Double(value.x)
      let visionY = Double(value.y)
      guard x.isFinite, visionY.isFinite else {
        return nil
      }

      // VNContour coordinates use Vision's lower-left origin. the native renderer and
      // the saved mask both expose top-left image coordinates.
      return CGPoint(
        x: CGFloat(min(max(x, 0), 1)),
        y: CGFloat(min(max(1 - visionY, 0), 1))
      )
    }
    points = removingDuplicateContourPoints(points)

    guard points.count >= 3 else {
      return []
    }

    points = resampledClosedContour(
      points,
      maximumPointCount: 250,
      pixelWidth: maskImage.width,
      pixelHeight: maskImage.height
    )

    return orderedContourPoints(
      points,
      subjectBounds: subjectBounds,
      pixelWidth: maskImage.width,
      pixelHeight: maskImage.height
    )
  }

  private func absoluteArea(of contour: VNContour) -> Double {
    let points = contour.normalizedPoints
    guard points.count >= 3 else {
      return 0
    }

    var doubledArea = 0.0
    for index in points.indices {
      let point = points[index]
      let nextPoint = points[(index + 1) % points.count]
      doubledArea += Double(point.x * nextPoint.y - nextPoint.x * point.y)
    }
    return abs(doubledArea) / 2
  }

  private func simplifiedContour(
    _ contour: VNContour,
    maximumPointCount: Int
  ) -> VNContour {
    guard contour.pointCount > maximumPointCount else {
      return contour
    }

    var bestContour = contour
    let epsilonCandidates: [Float] = [
      0.0005,
      0.00075,
      0.001,
      0.0015,
      0.002,
      0.003,
      0.0045,
      0.0065,
      0.009
    ]

    for epsilon in epsilonCandidates {
      guard
        let candidate = try? contour.polygonApproximation(epsilon: epsilon),
        candidate.pointCount >= 3
      else {
        continue
      }

      bestContour = candidate
      if candidate.pointCount <= maximumPointCount {
        break
      }
    }

    return bestContour
  }

  private func removingDuplicateContourPoints(_ points: [CGPoint]) -> [CGPoint] {
    let duplicateToleranceSquared = 1e-12
    var result = [CGPoint]()
    result.reserveCapacity(points.count)

    for point in points {
      if
        let previousPoint = result.last,
        squaredDistance(from: previousPoint, to: point) <= duplicateToleranceSquared
      {
        continue
      }
      result.append(point)
    }

    if
      result.count > 1,
      let firstPoint = result.first,
      let lastPoint = result.last,
      squaredDistance(from: firstPoint, to: lastPoint) <= duplicateToleranceSquared
    {
      result.removeLast()
    }

    return result
  }

  private func resampledClosedContour(
    _ points: [CGPoint],
    maximumPointCount: Int,
    pixelWidth: Int,
    pixelHeight: Int
  ) -> [CGPoint] {
    guard points.count >= 3, maximumPointCount >= 3 else {
      return points
    }

    // Always resample, even when Vision already returned fewer than the cap.
    // Consumers can then treat index / count as a stable perimeter fraction.
    let minimumPointCount = min(64, maximumPointCount)
    let samplePointCount = max(
      minimumPointCount,
      min(points.count, maximumPointCount)
    )

    var segmentLengths = [Double]()
    segmentLengths.reserveCapacity(points.count)
    var totalLength = 0.0

    for index in points.indices {
      let length = pixelDistance(
        from: points[index],
        to: points[(index + 1) % points.count],
        pixelWidth: pixelWidth,
        pixelHeight: pixelHeight
      )
      segmentLengths.append(length)
      totalLength += length
    }

    guard totalLength > 0 else {
      return Array(points.prefix(samplePointCount))
    }

    let spacing = totalLength / Double(samplePointCount)
    var result = [CGPoint]()
    result.reserveCapacity(samplePointCount)
    var segmentIndex = 0
    var segmentStartDistance = 0.0

    for sampleIndex in 0..<samplePointCount {
      let targetDistance = Double(sampleIndex) * spacing
      while
        segmentIndex < segmentLengths.count - 1,
        segmentStartDistance + segmentLengths[segmentIndex] < targetDistance
      {
        segmentStartDistance += segmentLengths[segmentIndex]
        segmentIndex += 1
      }

      let startPoint = points[segmentIndex]
      let endPoint = points[(segmentIndex + 1) % points.count]
      let segmentLength = segmentLengths[segmentIndex]
      let progress = segmentLength > 0
        ? (targetDistance - segmentStartDistance) / segmentLength
        : 0
      let interpolation = CGFloat(progress)
      result.append(CGPoint(
        x: startPoint.x + (endPoint.x - startPoint.x) * interpolation,
        y: startPoint.y + (endPoint.y - startPoint.y) * interpolation
      ))
    }

    return result
  }

  private func orderedContourPoints(
    _ points: [CGPoint],
    subjectBounds: CGRect,
    pixelWidth: Int,
    pixelHeight: Int
  ) -> [CGPoint] {
    guard points.count >= 3 else {
      return points
    }

    let minimumTopBandHeight = 2 / CGFloat(max(pixelHeight, 1))
    let topBandMaximumY = subjectBounds.minY
      + max(subjectBounds.height * 0.28, minimumTopBandHeight)
    let topBandIndices = points.indices.filter { points[$0].y <= topBandMaximumY }
    let candidateIndices = topBandIndices.isEmpty ? Array(points.indices) : topBandIndices
    let horizontalScale = max(subjectBounds.width, 1 / CGFloat(max(pixelWidth, 1)))
    let verticalScale = max(subjectBounds.height, 1 / CGFloat(max(pixelHeight, 1)))

    func cornerDistance(for index: Int, target: CGPoint) -> Double {
      let horizontalDistance = Double((points[index].x - target.x) / horizontalScale)
      let verticalDistance = Double((points[index].y - target.y) / verticalScale)
      return horizontalDistance * horizontalDistance
        + verticalDistance * verticalDistance
    }

    let topRightTarget = CGPoint(x: subjectBounds.maxX, y: subjectBounds.minY)
    let topLeftTarget = CGPoint(x: subjectBounds.minX, y: subjectBounds.minY)
    guard
      let startIndex = candidateIndices.min(by: {
        cornerDistance(for: $0, target: topRightTarget)
          < cornerDistance(for: $1, target: topRightTarget)
      }),
      let topLeftIndex = candidateIndices.min(by: {
        cornerDistance(for: $0, target: topLeftTarget)
          < cornerDistance(for: $1, target: topLeftTarget)
      })
    else {
      return points
    }

    let forwardDistance = contourDistance(
      in: points,
      from: startIndex,
      to: topLeftIndex,
      step: 1,
      pixelWidth: pixelWidth,
      pixelHeight: pixelHeight
    )
    let reverseDistance = contourDistance(
      in: points,
      from: startIndex,
      to: topLeftIndex,
      step: -1,
      pixelWidth: pixelWidth,
      pixelHeight: pixelHeight
    )
    let step = forwardDistance <= reverseDistance ? 1 : -1

    return (0..<points.count).map { offset in
      let index = (startIndex + step * offset + points.count * 2) % points.count
      return points[index]
    }
  }

  private func contourDistance(
    in points: [CGPoint],
    from startIndex: Int,
    to targetIndex: Int,
    step: Int,
    pixelWidth: Int,
    pixelHeight: Int
  ) -> Double {
    guard startIndex != targetIndex else {
      return 0
    }

    var distance = 0.0
    var currentIndex = startIndex
    for _ in 0..<points.count {
      let nextIndex = (currentIndex + step + points.count) % points.count
      distance += pixelDistance(
        from: points[currentIndex],
        to: points[nextIndex],
        pixelWidth: pixelWidth,
        pixelHeight: pixelHeight
      )
      if nextIndex == targetIndex {
        return distance
      }
      currentIndex = nextIndex
    }

    return .greatestFiniteMagnitude
  }

  private func pixelDistance(
    from startPoint: CGPoint,
    to endPoint: CGPoint,
    pixelWidth: Int,
    pixelHeight: Int
  ) -> Double {
    let horizontalDistance = Double(endPoint.x - startPoint.x) * Double(pixelWidth)
    let verticalDistance = Double(endPoint.y - startPoint.y) * Double(pixelHeight)
    return hypot(horizontalDistance, verticalDistance)
  }

  private func squaredDistance(from startPoint: CGPoint, to endPoint: CGPoint) -> Double {
    let horizontalDistance = Double(endPoint.x - startPoint.x)
    let verticalDistance = Double(endPoint.y - startPoint.y)
    return horizontalDistance * horizontalDistance + verticalDistance * verticalDistance
  }

  private func instanceIdentifier(
    at normalizedPoint: CGPoint,
    in instanceMask: CVPixelBuffer
  ) -> Int? {
    let width = CVPixelBufferGetWidth(instanceMask)
    let height = CVPixelBufferGetHeight(instanceMask)
    guard width > 0, height > 0 else {
      return nil
    }

    guard CVPixelBufferLockBaseAddress(instanceMask, .readOnly) == kCVReturnSuccess else {
      return nil
    }
    defer {
      CVPixelBufferUnlockBaseAddress(instanceMask, .readOnly)
    }

    guard let baseAddress = CVPixelBufferGetBaseAddress(instanceMask) else {
      return nil
    }

    let x = min(max(Int((normalizedPoint.x * CGFloat(width - 1)).rounded()), 0), width - 1)
    let y = min(max(Int((normalizedPoint.y * CGFloat(height - 1)).rounded()), 0), height - 1)
    let row = baseAddress.advanced(by: y * CVPixelBufferGetBytesPerRow(instanceMask))

    switch CVPixelBufferGetPixelFormatType(instanceMask) {
    case kCVPixelFormatType_OneComponent8:
      return Int(row.assumingMemoryBound(to: UInt8.self)[x])

    case kCVPixelFormatType_OneComponent16Half:
      let value = Float(Float16(bitPattern: row.assumingMemoryBound(to: UInt16.self)[x]))
      return value.isFinite ? Int(value.rounded()) : nil

    case kCVPixelFormatType_OneComponent32Float:
      let value = row.assumingMemoryBound(to: Float.self)[x]
      return value.isFinite ? Int(value.rounded()) : nil

    default:
      return nil
    }
  }

  private func nearestInstanceIdentifier(
    to normalizedPoint: CGPoint,
    in instanceMask: CVPixelBuffer,
    allowedIdentifiers: IndexSet,
    requestId: String
  ) throws -> Int? {
    let width = CVPixelBufferGetWidth(instanceMask)
    let height = CVPixelBufferGetHeight(instanceMask)
    guard width > 0, height > 0 else {
      return nil
    }

    let pixelFormat = CVPixelBufferGetPixelFormatType(instanceMask)
    guard
      pixelFormat == kCVPixelFormatType_OneComponent8
        || pixelFormat == kCVPixelFormatType_OneComponent16Half
        || pixelFormat == kCVPixelFormatType_OneComponent32Float
    else {
      // The caller deliberately falls back to the first Vision instance when
      // this SDK/device exposes an instance-mask format we cannot decode.
      return nil
    }

    guard CVPixelBufferLockBaseAddress(instanceMask, .readOnly) == kCVReturnSuccess else {
      return nil
    }
    defer {
      CVPixelBufferUnlockBaseAddress(instanceMask, .readOnly)
    }

    guard let baseAddress = CVPixelBufferGetBaseAddress(instanceMask) else {
      return nil
    }

    let targetX = normalizedPoint.x * CGFloat(width - 1)
    let targetY = normalizedPoint.y * CGFloat(height - 1)
    let bytesPerRow = CVPixelBufferGetBytesPerRow(instanceMask)
    var nearestIdentifier: Int?
    var nearestDistanceSquared = Double.greatestFiniteMagnitude

    for rowIndex in 0..<height {
      if rowIndex.isMultiple(of: 64) {
        try requestRegistry.throwIfCancelled(requestId)
      }

      let row = baseAddress.advanced(by: rowIndex * bytesPerRow)
      let verticalDistance = Double(CGFloat(rowIndex) - targetY)
      let verticalDistanceSquared = verticalDistance * verticalDistance

      for columnIndex in 0..<width {
        let identifier: Int?
        switch pixelFormat {
        case kCVPixelFormatType_OneComponent8:
          identifier = Int(row.assumingMemoryBound(to: UInt8.self)[columnIndex])

        case kCVPixelFormatType_OneComponent16Half:
          let value = Float(
            Float16(bitPattern: row.assumingMemoryBound(to: UInt16.self)[columnIndex])
          )
          identifier = value.isFinite ? Int(value.rounded()) : nil

        case kCVPixelFormatType_OneComponent32Float:
          let value = row.assumingMemoryBound(to: Float.self)[columnIndex]
          identifier = value.isFinite ? Int(value.rounded()) : nil

        default:
          identifier = nil
        }

        guard
          let identifier,
          identifier != 0,
          allowedIdentifiers.contains(identifier)
        else {
          continue
        }

        let horizontalDistance = Double(CGFloat(columnIndex) - targetX)
        let distanceSquared = horizontalDistance * horizontalDistance
          + verticalDistanceSquared
        if distanceSquared < nearestDistanceSquared {
          nearestDistanceSquared = distanceSquared
          nearestIdentifier = identifier
        }
      }
    }

    return nearestIdentifier
  }

  private func validateRequestId(_ requestId: String) throws {
    guard !requestId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
      throw segmentationError(
        code: "ERR_SUBJECT_SEGMENTATION_REQUEST_ID",
        description: "requestId must be a non-empty string."
      )
    }
  }

  private func validateTargetDimensions(width: Int, height: Int) throws {
    let maximumDimension = 4_096
    let maximumPixelCount = 16_777_216

    guard
      width > 0,
      height > 0,
      width <= maximumDimension,
      height <= maximumDimension,
      width <= maximumPixelCount / height
    else {
      throw segmentationError(
        code: "ERR_SUBJECT_SEGMENTATION_TARGET_SIZE",
        description: "targetWidth and targetHeight must be positive, at most 4096, and no more than 16 megapixels in total."
      )
    }
  }

  private func localFileURL(from uriOrPath: String) throws -> URL {
    let trimmed = uriOrPath.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty else {
      throw segmentationError(
        code: "ERR_SUBJECT_SEGMENTATION_URI",
        description: "A non-empty local file URI is required."
      )
    }

    if trimmed.hasPrefix("file://") {
      guard let url = URL(string: trimmed), url.isFileURL else {
        throw segmentationError(
          code: "ERR_SUBJECT_SEGMENTATION_URI",
          description: "The provided file URI is invalid."
        )
      }
      return url.standardizedFileURL
    }

    guard trimmed.hasPrefix("/") else {
      throw segmentationError(
        code: "ERR_SUBJECT_SEGMENTATION_URI",
        description: "Only file:// URIs and absolute local file paths are supported."
      )
    }

    return URL(fileURLWithPath: trimmed).standardizedFileURL
  }

  private func normalizedToZeroOrigin(_ image: CIImage) -> CIImage {
    let extent = image.extent
    guard extent.minX != 0 || extent.minY != 0 else {
      return image
    }

    return image.transformed(by: CGAffineTransform(
      translationX: -extent.minX,
      y: -extent.minY
    ))
  }

  private func centerCropRect(
    sourceExtent: CGRect,
    targetWidth: Int,
    targetHeight: Int
  ) -> CGRect {
    let targetAspectRatio = CGFloat(targetWidth) / CGFloat(targetHeight)
    let sourceAspectRatio = sourceExtent.width / sourceExtent.height

    if sourceAspectRatio > targetAspectRatio {
      let cropWidth = sourceExtent.height * targetAspectRatio
      return CGRect(
        x: sourceExtent.midX - cropWidth / 2,
        y: sourceExtent.minY,
        width: cropWidth,
        height: sourceExtent.height
      )
    }

    let cropHeight = sourceExtent.width / targetAspectRatio
    return CGRect(
      x: sourceExtent.minX,
      y: sourceExtent.midY - cropHeight / 2,
      width: sourceExtent.width,
      height: cropHeight
    )
  }

  private func makeOutputURL(pathExtension: String) throws -> URL {
    let directory = outputDirectory
    do {
      try FileManager.default.createDirectory(
        at: directory,
        withIntermediateDirectories: true
      )
    } catch {
      throw segmentationError(
        code: "ERR_SUBJECT_SEGMENTATION_OUTPUT_DIRECTORY",
        description: "The subject segmentation cache directory could not be created.",
        cause: error
      )
    }

    return directory
      .appendingPathComponent(UUID().uuidString, isDirectory: false)
      .appendingPathExtension(pathExtension)
  }

  private var outputDirectory: URL {
    FileManager.default.temporaryDirectory
      .appendingPathComponent("SubjectSegmentation", isDirectory: true)
  }

  private func writeImage(
    _ image: CGImage,
    to url: URL,
    type: CFString,
    properties: [CFString: Any]?
  ) throws {
    guard let destination = CGImageDestinationCreateWithURL(url as CFURL, type, 1, nil) else {
      throw segmentationError(
        code: "ERR_SUBJECT_SEGMENTATION_IMAGE_WRITE",
        description: "The image encoder could not create '\(url.lastPathComponent)'."
      )
    }

    CGImageDestinationAddImage(destination, image, properties as CFDictionary?)
    guard CGImageDestinationFinalize(destination) else {
      throw segmentationError(
        code: "ERR_SUBJECT_SEGMENTATION_IMAGE_WRITE",
        description: "The image encoder could not write '\(url.lastPathComponent)'."
      )
    }
  }

  func releaseOwnedFiles(_ uris: [String]) throws {
    let root = outputDirectory.standardizedFileURL.path + "/"

    for uri in uris {
      guard let url = try? localFileURL(from: uri) else {
        continue
      }

      let standardizedURL = url.standardizedFileURL
      guard standardizedURL.path.hasPrefix(root) else {
        continue
      }

      guard FileManager.default.fileExists(atPath: standardizedURL.path) else {
        continue
      }

      do {
        try FileManager.default.removeItem(at: standardizedURL)
      } catch {
        throw segmentationError(
          code: "ERR_SUBJECT_SEGMENTATION_RELEASE",
          description: "Could not release cached file '\(standardizedURL.lastPathComponent)'.",
          cause: error
        )
      }
    }
  }
}

private func maskByte(from value: Float) -> UInt8 {
  guard value.isFinite else {
    return 0
  }

  let clamped = min(max(value, 0), 1)
  return UInt8((clamped * 255).rounded())
}

private func segmentationError(code: String, description: String, cause: Error? = nil) -> NSError {
  var info: [String: Any] = [NSLocalizedDescriptionKey: description]
  if let cause { info[NSUnderlyingErrorKey] = cause }
  return NSError(domain: code, code: 1, userInfo: info)
}
