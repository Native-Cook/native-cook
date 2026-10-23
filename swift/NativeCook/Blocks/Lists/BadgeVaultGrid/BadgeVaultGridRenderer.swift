import Metal
import MetalKit
import SwiftUI
import simd

private struct BadgeVaultGridVertexUniforms {
    var rect: SIMD4<Float>
    var viewport: SIMD2<Float>
}

private struct BadgeVaultGridBadgeUniforms {
    var uCentre: SIMD2<Float>
    var uR: Float
    var uRot: simd_float3x3
    var uA: SIMD3<Float>
    var uB: SIMD3<Float>
    var uC: SIMD3<Float>
    var uTile: Float
    var uEnv: SIMD2<Float>
    var uSheen: Float
    var uFill: Float
    var uWave: Float
    var uTime: Float
    var uLift: Float
    var uPresent: Float
    var uSocket: Float
    var uOpacity: Float
}

private struct BadgeVaultGridFloodUniforms {
    var uSize: SIMD2<Float>
    var uOrigin: SIMD2<Float>
    var uCentre: SIMD2<Float>
    var uR: Float
    var uColour: SIMD3<Float>
    var uSpread: Float
    var uPower: Float
    var uRays: Float
    var uSince: Float
    var uTime: Float
    var uOpacity: Float
}

private struct BadgeVaultGridPillUniforms {
    var uSize: SIMD2<Float>
    var uOrigin: SIMD2<Float>
    var uPress: Float
    var uSheen: Float
    var uDark: Float
    var uTint: SIMD3<Float>
    var uOpacity: Float
}

struct BadgeVaultGridMetalView: UIViewRepresentable {
    enum Layer { case grid, hero }
    let layer: Layer
    let engine: BadgeVaultGridEngine
    let serial: Int

    func makeCoordinator() -> BadgeVaultGridMetalRenderer {
        BadgeVaultGridMetalRenderer(layer: layer, engine: engine)
    }

    func makeUIView(context: Context) -> MTKView {
        let view = MTKView(frame: .zero, device: context.coordinator.device)
        view.delegate = context.coordinator
        view.colorPixelFormat = .bgra8Unorm
        view.framebufferOnly = true
        view.isOpaque = layer == .grid
        view.backgroundColor = .clear
        view.isPaused = true
        view.enableSetNeedsDisplay = true
        view.preferredFramesPerSecond = 60
        if layer == .hero {
            let labels = BadgeVaultGridHeroLabels()
            labels.tag = 8417
            labels.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            view.addSubview(labels)
        }
        return view
    }

    func updateUIView(_ view: MTKView, context: Context) {
        context.coordinator.engine = engine
        if layer == .hero, let labels = view.viewWithTag(8417) as? BadgeVaultGridHeroLabels {
            labels.frame = view.bounds
            labels.update(engine)
        }
        view.draw()
    }
}

@MainActor final class BadgeVaultGridMetalRenderer: NSObject, MTKViewDelegate {
    let device: MTLDevice?
    private let commandQueue: MTLCommandQueue?
    private let badgePipeline: MTLRenderPipelineState?
    private let floodPipeline: MTLRenderPipelineState?
    private let pillPipeline: MTLRenderPipelineState?
    private let atlas: MTLTexture?
    private let atlasSampler: MTLSamplerState?
    private let layer: BadgeVaultGridMetalView.Layer
    var engine: BadgeVaultGridEngine

    init(layer: BadgeVaultGridMetalView.Layer, engine: BadgeVaultGridEngine) {
        self.layer = layer
        self.engine = engine
        let device = MTLCreateSystemDefaultDevice()
        self.device = device
        self.commandQueue = device?.makeCommandQueue()
        let library = device?.makeDefaultLibrary()
        self.badgePipeline = Self.pipeline(device: device, library: library, fragment: "badgeVaultGridBadge")
        self.floodPipeline = Self.pipeline(device: device, library: library, fragment: "badgeVaultGridFlood")
        self.pillPipeline = Self.pipeline(device: device, library: library, fragment: "badgeVaultGridPill")
        if let device, let url = Bundle.main.url(forResource: "BadgeVaultGridAtlas", withExtension: "png") {
            self.atlas = try? MTKTextureLoader(device: device).newTexture(
                URL: url, options: [.SRGB: false, .origin: MTKTextureLoader.Origin.topLeft])
        } else {
            self.atlas = nil
        }
        let sampler = MTLSamplerDescriptor()
        sampler.minFilter = .linear
        sampler.magFilter = .linear
        sampler.sAddressMode = .clampToEdge
        sampler.tAddressMode = .clampToEdge
        self.atlasSampler = device?.makeSamplerState(descriptor: sampler)
    }

    private static func pipeline(device: MTLDevice?, library: MTLLibrary?, fragment: String) -> MTLRenderPipelineState? {
        guard let device, let library else { return nil }
        let descriptor = MTLRenderPipelineDescriptor()
        descriptor.vertexFunction = library.makeFunction(name: "badgeVaultGridVertex")
        descriptor.fragmentFunction = library.makeFunction(name: fragment)
        descriptor.colorAttachments[0].pixelFormat = .bgra8Unorm
        descriptor.colorAttachments[0].isBlendingEnabled = true
        descriptor.colorAttachments[0].sourceRGBBlendFactor = .one
        descriptor.colorAttachments[0].sourceAlphaBlendFactor = .one
        descriptor.colorAttachments[0].destinationRGBBlendFactor = .oneMinusSourceAlpha
        descriptor.colorAttachments[0].destinationAlphaBlendFactor = .oneMinusSourceAlpha
        return try? device.makeRenderPipelineState(descriptor: descriptor)
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

    func draw(in view: MTKView) {
        guard let drawable = view.currentDrawable,
              let queue = commandQueue,
              let command = queue.makeCommandBuffer(),
              let pass = view.currentRenderPassDescriptor else { return }
        pass.colorAttachments[0].clearColor = MTLClearColorMake(0, 0, 0, layer == .grid ? 1 : 0)
        pass.colorAttachments[0].loadAction = .clear
        guard let encoder = command.makeRenderCommandEncoder(descriptor: pass) else { return }
        let layout = engine.layout
        let viewport = SIMD2<Float>(Float(layout.width), Float(layout.height))
        if layer == .grid {
            drawGrid(encoder, viewport: viewport)
        } else {
            drawHero(encoder, viewport: viewport)
        }
        encoder.endEncoding()
        command.present(drawable)
        command.commit()
    }

    private func vertices(_ encoder: MTLRenderCommandEncoder, rect: CGRect, viewport: SIMD2<Float>) {
        var uniforms = BadgeVaultGridVertexUniforms(
            rect: SIMD4(Float(rect.minX), Float(rect.minY), Float(rect.width), Float(rect.height)),
            viewport: viewport)
        encoder.setVertexBytes(&uniforms, length: MemoryLayout<BadgeVaultGridVertexUniforms>.stride, index: 0)
    }

    private func badge(_ encoder: MTLRenderCommandEncoder, index: Int, pose: BadgeVaultGridPose,
                       rect: CGRect, viewport: SIMD2<Float>, opacity: Double) {
        guard let badgePipeline, let atlas, let atlasSampler else { return }
        let badge = BadgeVaultGridBadge.all[index]
        let sx = sin(-pose.tiltX)
        let cx = cos(-pose.tiltX)
        let sy = sin(pose.spin + pose.tiltY)
        let cy = cos(pose.spin + pose.tiltY)
        let sz = sin(-pose.roll)
        let cz = cos(-pose.roll)
        let rotation = simd_float3x3(columns: (
            SIMD3(Float(cz * cy), Float(sz * cy), Float(-sy)),
            SIMD3(Float(cz * sy * sx - sz * cx), Float(sz * sy * sx + cz * cx), Float(cy * sx)),
            SIMD3(Float(cz * sy * cx + sz * sx), Float(sz * sy * cx - cz * sx), Float(cy * cx))
        ))
        var uniforms = BadgeVaultGridBadgeUniforms(
            uCentre: SIMD2(Float(pose.cx), Float(pose.cy)),
            uR: Float(pose.r), uRot: rotation,
            uA: badge.a, uB: badge.b, uC: badge.c, uTile: Float(badge.tile),
            uEnv: SIMD2(Float(pose.envX), Float(pose.envY)),
            uSheen: Float(pose.sheen), uFill: Float(pose.fill), uWave: Float(pose.wave),
            uTime: Float(pose.time), uLift: Float(pose.lift),
            uPresent: Float(pose.present), uSocket: Float(pose.socket),
            uOpacity: Float(opacity))
        encoder.setRenderPipelineState(badgePipeline)
        vertices(encoder, rect: rect, viewport: viewport)
        encoder.setFragmentBytes(&uniforms, length: MemoryLayout<BadgeVaultGridBadgeUniforms>.stride, index: 0)
        encoder.setFragmentTexture(atlas, index: 0)
        encoder.setFragmentSamplerState(atlasSampler, index: 0)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 6)
    }

    private func pill(_ encoder: MTLRenderCommandEncoder, rect: CGRect, viewport: SIMD2<Float>,
                      tint: SIMD3<Float>, press: Double, dark: Double = 0, opacity: Double = 1) {
        guard let pillPipeline else { return }
        var uniforms = BadgeVaultGridPillUniforms(
            uSize: SIMD2(Float(rect.width), Float(rect.height)),
            uOrigin: SIMD2(Float(rect.minX), Float(rect.minY)),
            uPress: Float(press), uSheen: -1, uDark: Float(dark),
            uTint: tint, uOpacity: Float(opacity))
        encoder.setRenderPipelineState(pillPipeline)
        vertices(encoder, rect: rect, viewport: viewport)
        encoder.setFragmentBytes(&uniforms, length: MemoryLayout<BadgeVaultGridPillUniforms>.stride, index: 0)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 6)
    }

    private func drawGrid(_ encoder: MTLRenderCommandEncoder, viewport: SIMD2<Float>) {
        let layout = engine.layout
        let recede = engine.hero.recede
        let scale = engine.reduced ? 1 : 1 - 0.06 * recede
        let opacity = 1 - 0.88 * recede
        for index in 0..<18 {
            let col = layout.column(index % 3)
            let rawY = layout.headerHeight + Double(index / 3) * layout.pitch - engine.scrollY
            if rawY + layout.canvasHeight < -10 || rawY > layout.height + 10 { continue }
            let shake = engine.visuals[index].shake * scale
            let x = layout.width / 2 + (col.x - layout.width / 2) * scale + shake
            let y = layout.height / 2 + (rawY - layout.height / 2) * scale
            let rect = CGRect(x: x, y: y, width: col.width * scale, height: layout.canvasHeight * scale)
            var pose = engine.visuals[index].pose
            pose.cx = layout.width / 2 + (pose.cx - layout.width / 2) * scale + shake
            pose.cy = layout.height / 2 + (pose.cy - layout.height / 2) * scale
            pose.r *= scale
            badge(encoder, index: index, pose: pose, rect: rect, viewport: viewport, opacity: opacity)
        }
        let footerY = layout.footerY - engine.scrollY
        if footerY < layout.height + 20 && footerY + 96 > -20 {
            let rect = CGRect(x: layout.width - 156, y: footerY + 24, width: 132, height: 48)
            pill(encoder, rect: rect, viewport: viewport,
                 tint: SIMD3(0.96, 0.95, 0.92), press: engine.pillPress,
                 dark: engine.count == 18 ? 1 : 0, opacity: opacity)
        }
    }

    private func drawHero(_ encoder: MTLRenderCommandEncoder, viewport: SIMD2<Float>) {
        let hero = engine.hero
        guard hero.index >= 0 else { return }
        let layout = engine.layout
        if let floodPipeline {
            let flood = hero.flood
            var uniforms = BadgeVaultGridFloodUniforms(
                uSize: viewport,
                uOrigin: SIMD2(Float(flood.ox), Float(flood.oy)),
                uCentre: SIMD2(Float(flood.cx), Float(flood.cy)),
                uR: Float(flood.r),
                uColour: BadgeVaultGridBadge.all[hero.index].a,
                uSpread: Float(flood.spread), uPower: Float(flood.power),
                uRays: Float(flood.rays), uSince: Float(flood.since),
                uTime: Float(flood.time), uOpacity: Float(hero.alpha))
            encoder.setRenderPipelineState(floodPipeline)
            vertices(encoder, rect: CGRect(x: 0, y: 0, width: layout.width, height: layout.height), viewport: viewport)
            encoder.setFragmentBytes(&uniforms, length: MemoryLayout<BadgeVaultGridFloodUniforms>.stride, index: 0)
            encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 6)
        }
        let pose = hero.pose
        let rect = CGRect(x: pose.cx - 1.35 * pose.r, y: pose.cy - 1.35 * pose.r,
                          width: 2.7 * pose.r, height: 2.7 * pose.r)
        badge(encoder, index: hero.index, pose: pose, rect: rect, viewport: viewport, opacity: hero.alpha)
        let reveal = BadgeVaultGridMath.clamp((hero.age - (engine.reduced ? 0 : 520)) / (engine.reduced ? 200 : 240)) * hero.details
        pill(encoder, rect: CGRect(x: 16, y: layout.safeTop + 4, width: 36, height: 36),
             viewport: viewport, tint: SIMD3(0.09, 0.09, 0.10),
             press: 0, opacity: reveal)
        let shareReveal = BadgeVaultGridMath.clamp((hero.age - (engine.reduced ? 0 : 960)) / (engine.reduced ? 200 : 240)) * hero.details
        pill(encoder, rect: CGRect(x: 24, y: layout.shareY, width: layout.width - 48, height: 56),
             viewport: viewport, tint: BadgeVaultGridBadge.all[hero.index].a,
             press: 0, opacity: shareReveal)
    }
}
