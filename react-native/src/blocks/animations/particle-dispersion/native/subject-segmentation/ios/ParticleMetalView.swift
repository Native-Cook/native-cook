import ExpoModulesCore
import Foundation
import Metal
import MetalKit
import QuartzCore
import UIKit

private struct ParticleFrameIdentity: Equatable {
  let frameUri: String
  let maskUri: String
}

// A queued preparation must not retain its view or publish obsolete textures.
private final class ParticlePreparationRequest {
  private let lock = NSLock()
  private var cancelled = false

  func cancel() {
    lock.lock()
    cancelled = true
    lock.unlock()
  }

  var isCancelled: Bool {
    lock.lock()
    defer { lock.unlock() }
    return cancelled
  }
}

/// Bound submitted GPU work without waiting for a free frame slot. A skipped
/// prop update redraws the newest state once an outstanding frame completes.
private final class ParticleFrameGate {
  private let lock = NSLock()
  private var inFlight = 0
  private var needsRetry = false

  func acquire() -> Bool {
    lock.lock()
    defer { lock.unlock() }
    guard inFlight < 2 else {
      needsRetry = true
      return false
    }
    inFlight += 1
    needsRetry = false
    return true
  }

  func finish() -> Bool {
    lock.lock()
    defer { lock.unlock() }
    inFlight -= 1
    let retry = needsRetry
    needsRetry = false
    return retry
  }
}

private enum ParticleMetalViewError: LocalizedError {
  case invalidFileURI
  case metalUnavailable
  case commandQueueUnavailable
  case commandBufferUnavailable
  case frameRenderingFailed

  var errorDescription: String? {
    switch self {
    case .invalidFileURI:
      return "Particle images must be readable local file URIs."
    case .metalUnavailable:
      return "Metal rendering is unavailable on this device."
    case .commandQueueUnavailable:
      return "The particle renderer could not create its command queue."
    case .commandBufferUnavailable:
      return "The particle renderer could not create a frame command buffer."
    case .frameRenderingFailed:
      return "The GPU could not render the particle frame."
    }
  }
}

/// Reanimated supplies the only clock. MTKView coalesces prop-driven redraws;
/// there is no independent display link and no image IO in the drawing path.
final class ParticleMetalView: ExpoView, MTKViewDelegate {
  let onReady = EventDispatcher()
  let onError = EventDispatcher()

  // URI setters intentionally do not load: Expo calls applyProps after the
  // complete prop batch, so a new frame never starts loading with the old mask.
  var frameUri = ""
  var maskUri = ""
  var timeSeconds = 0.0 { didSet { requestRender() } }
  var progress = 0.0 { didSet { requestRender() } }
  var dustStartSeconds = 0.417 { didSet { requestRender() } }
  var dustDurationSeconds = 2.6 { didSet { requestRender() } }
  var seed = 1.0 { didSet { requestRender() } }
  var reduceMotion = false { didSet { requestRender() } }
  var edgeParticleStrength = 0.0 { didSet { requestRender() } }

  private static let preparationQueue = DispatchQueue(
    label: "SubjectSegmentation.ParticlePreparation",
    qos: .userInitiated
  )

  private let metalView = MTKView(frame: .zero, device: nil)
  private let frameGate = ParticleFrameGate()
  private let performanceProbe = ParticlePerformanceProbe()
  private var particleBackgroundColor = SIMD4<Float>(0, 0, 0, 1)
  private var particleSubjectBounds = SIMD4<Float>(0, 0, 1, 1)
  private var renderer: ParticleRenderer?
  private var textures: ParticleTextures?
  private var commandQueue: MTLCommandQueue?
  private var preparationRequest: ParticlePreparationRequest?
  private var requestedIdentity: ParticleFrameIdentity?
  private var loadedIdentity: ParticleFrameIdentity?
  private var generation: UInt64 = 0
  private var readyGeneration: UInt64?
  private var errorGeneration: UInt64?

  required init(appContext: AppContext? = nil) {
    super.init(appContext: appContext)
    clipsToBounds = true
    isOpaque = false
    backgroundColor = .clear
    isUserInteractionEnabled = false

    metalView.isPaused = true
    metalView.enableSetNeedsDisplay = true
    metalView.autoResizeDrawable = true
    metalView.colorPixelFormat = .bgra8Unorm
    // The texture loader preserves the capture's sRGB channel values. Tag the
    // drawable layer likewise so it composites with the sRGB Skia foreground.
    (metalView.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name: CGColorSpace.sRGB)
    metalView.framebufferOnly = true
    metalView.isOpaque = false
    metalView.backgroundColor = .clear
    metalView.clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 0)
    metalView.isUserInteractionEnabled = false
    metalView.isHidden = true
    metalView.delegate = self
    addSubview(metalView)
  }

  deinit {
    performanceProbe?.finish(reason: "view_deinit")
    preparationRequest?.cancel()
    metalView.delegate = nil
    metalView.releaseDrawables()
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    metalView.frame = bounds
    let scale = max(window?.screen.scale ?? traitCollection.displayScale, 1)
    metalView.contentScaleFactor = scale
    let size = CGSize(width: bounds.width * scale, height: bounds.height * scale)
    if metalView.drawableSize != size {
      metalView.drawableSize = size
    }
    requestRender()
  }

  override func didMoveToWindow() {
    super.didMoveToWindow()
    if window == nil {
      releaseResources()
    } else {
      metalView.delegate = self
      applyProps()
    }
  }

  func setParticleBackgroundColor(_ values: [Double]) {
    guard values.count == 4, values.allSatisfy({ $0.isFinite }) else { return }
    particleBackgroundColor = SIMD4<Float>(
      Float(min(max(values[0], 0), 1)),
      Float(min(max(values[1], 0), 1)),
      Float(min(max(values[2], 0), 1)),
      Float(min(max(values[3], 0), 1))
    )
    requestRender()
  }

  func setParticleSubjectBounds(_ values: [Double]) {
    guard values.count == 4, values.allSatisfy({ $0.isFinite }) else { return }
    let x = min(max(values[0], 0), 1)
    let y = min(max(values[1], 0), 1)
    particleSubjectBounds = SIMD4<Float>(
      Float(x), Float(y),
      Float(min(max(values[2], 0), 1 - x)),
      Float(min(max(values[3], 0), 1 - y))
    )
    requestRender()
  }

  func applyProps() {
    let identity = ParticleFrameIdentity(frameUri: frameUri, maskUri: maskUri)
    guard identity != requestedIdentity else {
      requestRender()
      return
    }

    releaseResources()
    guard window != nil, !frameUri.isEmpty, !maskUri.isEmpty else { return }
    requestedIdentity = identity
    let activeGeneration = generation
    let request = ParticlePreparationRequest()
    preparationRequest = request
    let performanceProbe = self.performanceProbe
    performanceProbe?.beginPreparation(generation: activeGeneration)
    let preparationStarted = performanceProbe == nil ? 0 : CACurrentMediaTime()

    Self.preparationQueue.async { [weak self] in
      var rendererMS = 0.0
      var texturesMS = 0.0
      func finishPreparation(_ status: String) {
        performanceProbe?.preparationFinished(generation: activeGeneration,
          rendererMS: rendererMS, texturesMS: texturesMS,
          totalMS: (CACurrentMediaTime() - preparationStarted) * 1_000, status: status)
      }
      guard !request.isCancelled else { finishPreparation("cancelled"); return }
      let result: Result<(MTLDevice, MTLCommandQueue, ParticleRenderer, ParticleTextures), Error>
      do {
        result = try autoreleasepool {
          let frameURL = try Self.localFileURL(identity.frameUri)
          let maskURL = try Self.localFileURL(identity.maskUri)
          guard let device = MTLCreateSystemDefaultDevice() else {
            throw ParticleMetalViewError.metalUnavailable
          }
          guard let queue = device.makeCommandQueue() else {
            throw ParticleMetalViewError.commandQueueUnavailable
          }
          let rendererStarted = performanceProbe == nil ? 0 : CACurrentMediaTime()
          let renderer = try ParticleRenderer(device: device, colorPixelFormat: .bgra8Unorm)
          if performanceProbe != nil { rendererMS = (CACurrentMediaTime() - rendererStarted) * 1_000 }
          guard !request.isCancelled else { return .failure(CancellationError()) }
          let texturesStarted = performanceProbe == nil ? 0 : CACurrentMediaTime()
          let textures = try renderer.loadTextures(frameURL: frameURL, maskURL: maskURL)
          if performanceProbe != nil { texturesMS = (CACurrentMediaTime() - texturesStarted) * 1_000 }
          return .success((device, queue, renderer, textures))
        }
      } catch {
        result = .failure(error)
      }
      guard !request.isCancelled else { finishPreparation("cancelled"); return }
      switch result {
      case .success: finishPreparation("success")
      case .failure: finishPreparation("failed")
      }

      DispatchQueue.main.async { [weak self] in
        guard let self,
          self.isCurrent(activeGeneration, identity: identity),
          !request.isCancelled
        else { return }
        self.preparationRequest = nil
        switch result {
        case let .success((device, queue, renderer, textures)):
          self.renderer = renderer
          self.textures = textures
          self.commandQueue = queue
          self.loadedIdentity = identity
          self.metalView.device = device
          self.metalView.delegate = self
          self.metalView.isHidden = false
          self.requestRender()
        case let .failure(error):
          self.reportError(error, generation: activeGeneration, identity: identity)
        }
      }
    }
  }

  func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {
    requestRender()
  }

  func draw(in view: MTKView) {
    guard window != nil, bounds.width > 0, bounds.height > 0,
      let identity = loadedIdentity,
      isCurrent(generation, identity: identity),
      errorGeneration != generation,
      let renderer, let textures, let commandQueue
    else { return }

    // currentRenderPassDescriptor implicitly acquires a drawable, which may
    // block when the GPU is behind. Bound queued work before asking for one.
    let performanceProbe = self.performanceProbe
    let drawStarted = performanceProbe == nil ? 0 : CACurrentMediaTime()
    let frameGate = self.frameGate
    guard frameGate.acquire() else { performanceProbe?.skipped(gate: true); return }
    var submitted = false
    defer {
      if !submitted { _ = frameGate.finish() }
    }
    guard
      let descriptor = view.currentRenderPassDescriptor,
      let drawable = view.currentDrawable
    else { performanceProbe?.skipped(gate: false); return }
    let drawableWaitMS = performanceProbe == nil ? 0 : (CACurrentMediaTime() - drawStarted) * 1_000

    let activeGeneration = generation
    guard let commandBuffer = commandQueue.makeCommandBuffer() else {
      reportError(
        ParticleMetalViewError.commandBufferUnavailable,
        generation: activeGeneration,
        identity: identity
      )
      return
    }

    let parameters = ParticleRenderParameters(
      logicalSize: SIMD2<Float>(Float(bounds.width), Float(bounds.height)),
      drawableSize: SIMD2<Float>(Float(drawable.texture.width), Float(drawable.texture.height)),
      timeSeconds: finiteFloat(timeSeconds, fallback: 0),
      progress: min(max(finiteFloat(progress, fallback: 0), 0), 1),
      dustStartSeconds: finiteFloat(dustStartSeconds, fallback: 0.417),
      dustDurationSeconds: max(finiteFloat(dustDurationSeconds, fallback: 2.6), 0.001),
      seed: finiteFloat(seed, fallback: 1),
      reduceMotion: reduceMotion,
      backgroundColor: particleBackgroundColor,
      subjectBounds: particleSubjectBounds,
      edgeParticleStrength: min(max(finiteFloat(edgeParticleStrength, fallback: 0), 0), 1)
    )
    let performanceToken = performanceProbe?.submitted(clock: Double(parameters.timeSeconds),
      progress: Double(parameters.progress), drawableWaitMS: drawableWaitMS)

    do {
      let encodeStarted = performanceProbe == nil ? 0 : CACurrentMediaTime()
      try renderer.encode(
        commandBuffer: commandBuffer,
        renderPassDescriptor: descriptor,
        parameters: parameters,
        textures: textures
      )
      if let performanceProbe, let performanceToken {
        performanceProbe.encoded(performanceToken, milliseconds: (CACurrentMediaTime() - encodeStarted) * 1_000)
        #if !targetEnvironment(simulator)
        drawable.addPresentedHandler { [weak performanceProbe] shown in
          performanceProbe?.presented(performanceToken, at: shown.presentedTime)
        }
        #endif
      }
      let needsReadyEvent = readyGeneration != activeGeneration
      commandBuffer.addCompletedHandler { [weak self] completed in
        if let performanceProbe, let performanceToken {
          performanceProbe.completed(performanceToken, start: completed.gpuStartTime,
            end: completed.gpuEndTime, failed: completed.status == .error)
        }
        let needsRetry = frameGate.finish()
        // Normal frames never dispatch an event or hop back to JS. Only the
        // first frame, a failure or a coalesced redraw need the main thread.
        guard needsRetry || needsReadyEvent || completed.status == .error else { return }
        let gpuError = completed.error
        let failed = completed.status == .error
        DispatchQueue.main.async { [weak self] in
          // A completed frame from an older capture may have held the last
          // slot; retry current props before checking that frame's identity.
          if needsRetry { self?.requestRender() }
          guard let self, self.isCurrent(activeGeneration, identity: identity) else { return }
          if failed {
            self.reportError(
              gpuError ?? ParticleMetalViewError.frameRenderingFailed,
              generation: activeGeneration,
              identity: identity
            )
          } else if completed.status == .completed,
            self.readyGeneration != activeGeneration,
            self.errorGeneration != activeGeneration {
            self.readyGeneration = activeGeneration
            self.onReady(["frameUri": identity.frameUri, "maskUri": identity.maskUri])
          }
        }
      }
      commandBuffer.present(drawable)
      submitted = true
      commandBuffer.commit()
    } catch {
      reportError(error, generation: activeGeneration, identity: identity)
    }
  }

  private func requestRender() {
    guard window != nil, renderer != nil, textures != nil, errorGeneration != generation else { return }
    performanceProbe?.observe(time: timeSeconds, progress: progress, dustStart: dustStartSeconds,
      dustDuration: dustDurationSeconds, reduced: reduceMotion, generation: generation)
    metalView.setNeedsDisplay()
  }

  private func isCurrent(_ candidate: UInt64, identity: ParticleFrameIdentity) -> Bool {
    window != nil && generation == candidate &&
      requestedIdentity == identity &&
      frameUri == identity.frameUri && maskUri == identity.maskUri
  }

  private func reportError(_ error: Error, generation candidate: UInt64, identity: ParticleFrameIdentity) {
    guard isCurrent(candidate, identity: identity), errorGeneration != candidate else { return }
    errorGeneration = candidate
    metalView.isHidden = true
    performanceProbe?.finish(reason: "render_error")
    onError([
      "frameUri": identity.frameUri,
      "maskUri": identity.maskUri,
      "message": error.localizedDescription
    ])
  }

  private func releaseResources() {
    performanceProbe?.finish(reason: "resources_released")
    generation &+= 1
    preparationRequest?.cancel()
    preparationRequest = nil
    requestedIdentity = nil
    loadedIdentity = nil
    readyGeneration = nil
    errorGeneration = nil
    renderer = nil
    textures = nil
    commandQueue = nil
    metalView.isHidden = true
    metalView.delegate = nil
    metalView.releaseDrawables()
    metalView.device = nil
  }

  private func finiteFloat(_ value: Double, fallback: Float) -> Float {
    let converted = Float(value)
    return converted.isFinite ? converted : fallback
  }

  private nonisolated static func localFileURL(_ value: String) throws -> URL {
    guard let url = URL(string: value), url.isFileURL,
      url.host == nil || url.host == "" || url.host == "localhost",
      url.path.hasPrefix("/"),
      FileManager.default.isReadableFile(atPath: url.path)
    else { throw ParticleMetalViewError.invalidFileURI }
    return url
  }
}
