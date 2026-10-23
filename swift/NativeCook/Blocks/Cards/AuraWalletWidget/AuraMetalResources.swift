import Metal

/// Immutable after construction on the compiler queue. Metal's device, queue and
/// pipeline objects support crossing queues; all encoding stays on the main actor.
struct AuraMetalResources: Sendable {
  let device: MTLDevice
  let queue: MTLCommandQueue
  let shell: MTLRenderPipelineState
  let page: MTLRenderPipelineState

  init?() throws {
    guard let device = MTLCreateSystemDefaultDevice(), let queue = device.makeCommandQueue()
    else { return nil }
    self.device = device
    self.queue = queue
    let library = try device.makeLibrary(source: AuraShader.source, options: nil)
    let descriptor = MTLRenderPipelineDescriptor()
    descriptor.vertexFunction = library.makeFunction(name: "auraVertex")
    descriptor.colorAttachments[0].pixelFormat = .bgra8Unorm
    descriptor.fragmentFunction = library.makeFunction(name: "auraPageFragment")
    page = try device.makeRenderPipelineState(descriptor: descriptor)
    descriptor.fragmentFunction = library.makeFunction(name: "auraShellFragment")
    shell = try device.makeRenderPipelineState(descriptor: descriptor)
  }
}
