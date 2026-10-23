import MetalKit
import UIKit

final class ParticleDispersionDispersionView: MTKView, MTKViewDelegate {
  var onComplete: (() -> Void)?
  var onError: ((Error) -> Void)?
  private var renderer: ParticleDispersionParticleRenderer?
  private var commands: MTLCommandQueue?
  private var textures: ParticleDispersionParticleTextures?
  private var scene: ParticleDispersionEffectScene?
  private var started: CFTimeInterval?
  private var elapsed: Double = 0
  private var playing = false
  private var suspended = false
  private var reduced = false
  private var lastTime: Float = 0
  private var lastProgress: Float = 0
  private let inFlight = DispatchSemaphore(value: 3)

  init() {
    super.init(frame: .zero, device: MTLCreateSystemDefaultDevice())
    colorPixelFormat = .bgra8Unorm
    framebufferOnly = true
    isPaused = true
    enableSetNeedsDisplay = true
    preferredFramesPerSecond = 60
    delegate = self
    isAccessibilityElement = false
    accessibilityIdentifier = "dispersion-canvas"
  }

  required init(coder: NSCoder) { fatalError("init(coder:) is unavailable") }

  func show(_ scene: ParticleDispersionEffectScene) throws {
    stop()
    guard let device else {
      throw NSError(domain: "Metal", code: 1,
                    userInfo: [NSLocalizedDescriptionKey: "Metal is unavailable on this device."])
    }
    if renderer == nil { renderer = try ParticleDispersionParticleRenderer(device: device) }
    if commands == nil { commands = device.makeCommandQueue() }
    guard commands != nil else { throw ParticleDispersionParticleRendererError.encoderUnavailable }
    textures = try renderer?.loadTextures(frameURL: scene.frame, maskURL: scene.mask)
    self.scene = scene
    lastTime = 0
    lastProgress = 0
    reduced = false
    setNeedsDisplay()
  }

  func play(reducedMotion: Bool) {
    guard scene != nil else { return }
    reduced = reducedMotion
    elapsed = 0
    started = nil
    playing = true
    suspended = false
    enableSetNeedsDisplay = false
    isPaused = false
  }

  func stop() {
    playing = false
    started = nil
    elapsed = 0
    suspended = false
    isPaused = true
    enableSetNeedsDisplay = true
  }

  func suspend() {
    guard playing, !suspended else { return }
    if let started { elapsed = CACurrentMediaTime() - started }
    suspended = true
    isPaused = true
  }

  func resume() {
    guard playing, suspended else { return }
    started = CACurrentMediaTime() - elapsed
    suspended = false
    isPaused = false
  }

  func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {
    if isPaused { setNeedsDisplay() }
  }

  func draw(in view: MTKView) {
    guard bounds.width > 0, bounds.height > 0,
          let renderer, let commands, let textures, let scene,
          let drawable = currentDrawable, let pass = currentRenderPassDescriptor,
          inFlight.wait(timeout: .now()) == .success else { return }
    guard let command = commands.makeCommandBuffer() else {
      inFlight.signal()
      fail(ParticleDispersionParticleRendererError.encoderUnavailable)
      return
    }
    let duration = reduced ? 0.260 : 3.9
    if playing && !suspended {
      let now = CACurrentMediaTime()
      if started == nil { started = now }
      elapsed = min(now - (started ?? now), duration)
      lastTime = reduced ? 0 : Float(elapsed)
      lastProgress = Float(elapsed / duration)
    }
    let params = ParticleDispersionParticleRenderParameters(
      logicalSize: SIMD2(Float(bounds.width), Float(bounds.height)),
      drawableSize: SIMD2(Float(drawableSize.width), Float(drawableSize.height)),
      timeSeconds: lastTime, progress: lastProgress, seed: 1, reduceMotion: reduced,
      backgroundColor: SIMD4(125.0 / 255, 97.0 / 255, 75.0 / 255, 1),
      subjectBounds: scene.displayBounds(in: bounds.size)
    )
    do {
      try renderer.encode(commandBuffer: command, renderPassDescriptor: pass,
                          parameters: params, textures: textures)
      let semaphore = inFlight
      command.addCompletedHandler { [weak self] result in
        semaphore.signal()
        if let error = result.error {
          DispatchQueue.main.async { self?.fail(error) }
        }
      }
      command.present(drawable)
      command.commit()
      if playing && elapsed >= duration {
        playing = false
        isPaused = true
        enableSetNeedsDisplay = true
        DispatchQueue.main.async { [weak self] in self?.onComplete?() }
      }
    } catch {
      inFlight.signal()
      fail(error)
    }
  }

  private func fail(_ error: Error) {
    stop()
    onError?(error)
  }
}
