import Metal
import MetalKit
import QuartzCore
import UIKit

/// Metal preserves the source's premultiplied, non-linear paint and analytic studio.
final class MetacardUnifyFanView: MTKView, MTKViewDelegate, UIGestureRecognizerDelegate {
    private typealias T = MetacardUnifyFanTimeline
    let motion = MetacardUnifyFanMotion()
    private var commandQueue: MTLCommandQueue!
    private var pipelines: [String: MTLRenderPipelineState] = [:]
    private var faces: [MTLTexture] = []
    private var chip: MTLTexture!
    private var shadow: MTLTexture!
    private var texts: [MTLTexture] = []
    private var fanLayer: MTLTexture?
    private var heroLayer: MTLTexture?
    private var sceneHero = false
    private var previousPoint = CGPoint.zero
    private var previousTime = 0.0
    private var fingerVelocity = CGPoint.zero
    private var heading: UIAccessibilityElement!
    private var control: UIAccessibilityElement!
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)

    init() {
        super.init(frame: .zero, device: MTLCreateSystemDefaultDevice())
        colorPixelFormat = .bgra8Unorm
        framebufferOnly = true
        preferredFramesPerSecond = 60
        clearColor = MTLClearColorMake(0, 0, 0, 1)
        backgroundColor = .black
        isOpaque = true
        isAccessibilityElement = false
        commandQueue = device!.makeCommandQueue()
        do {
            try makePipelines()
            let loader = MTKTextureLoader(device: device!)
            faces = try (0..<8).map { try MetacardUnifyFanAssets.image("card-\($0)", loader: loader) }
            chip = try MetacardUnifyFanAssets.image("hero-chip", loader: loader)
            shadow = try MetacardUnifyFanAssets.image("card-shadow", loader: loader)
            texts = try (0..<7).map { try MetacardUnifyFanAssets.text($0, loader: loader) }
        } catch {
            assertionFailure("Metacard resources: \(error)")
            isPaused = true
            return
        }
        heading = UIAccessibilityElement(accessibilityContainer: self)
        heading.accessibilityTraits = .header
        control = MetacardUnifyFanAccessibilityControl(accessibilityContainer: self)
        (control as? MetacardUnifyFanAccessibilityControl)?.activate = { [weak self] in self?.motion.toggle() }
        control.accessibilityTraits = .button
        accessibilityElements = [heading!, control!]
        updateAccessibility()
        let touch = UILongPressGestureRecognizer(target: self, action: #selector(handleTouch(_:)))
        touch.minimumPressDuration = 0
        touch.allowableMovement = .greatestFiniteMagnitude
        touch.cancelsTouchesInView = false
        touch.delegate = self
        addGestureRecognizer(touch)
        motion.haptic = { [weak self] heavy in
            if heavy { self?.medium.impactOccurred() } else { self?.light.impactOccurred() }
        }
        delegate = self
    }

    required init(coder: NSCoder) { fatalError("Use init()") }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        isPaused = window == nil
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        updateAccessibility()
    }

    private func makePipelines() throws {
        let library = device!.makeDefaultLibrary()!
        let names = ["PAGE", "FAN", "FAN_SHADOW", "DARKNESS", "BLOOM", "HERO_SHADOW", "HERO", "HALO", "BOUNCE", "INNER_LIGHT", "TEXT", "COMPOSITE"]
        for name in names {
            let descriptor = MTLRenderPipelineDescriptor()
            descriptor.vertexFunction = library.makeFunction(name: "metacardUnifyFanVertex")
            descriptor.fragmentFunction = library.makeFunction(name: "metacardUnifyFan\(name)")
            let attachment = descriptor.colorAttachments[0]!
            attachment.pixelFormat = .bgra8Unorm
            attachment.isBlendingEnabled = true
            attachment.sourceRGBBlendFactor = name == "DARKNESS" ? .destinationAlpha : .one
            attachment.destinationRGBBlendFactor = name == "BOUNCE" ? .one : .oneMinusSourceAlpha
            attachment.sourceAlphaBlendFactor = name == "DARKNESS" ? .zero : .one
            attachment.destinationAlphaBlendFactor = name == "DARKNESS" ? .one : .oneMinusSourceAlpha
            pipelines[name] = try device!.makeRenderPipelineState(descriptor: descriptor)
        }
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {
        fanLayer = nil
        heroLayer = nil
    }

    func draw(in view: MTKView) {
        guard !pipelines.isEmpty, let drawable = currentDrawable, let pass = currentRenderPassDescriptor,
              let command = commandQueue.makeCommandBuffer() else { return }
        motion.advance(CACurrentMediaTime())
        if sceneHero != (motion.u >= 0.5) {
            sceneHero = motion.u >= 0.5
            updateAccessibility()
        }
        let u = motion.u
        let reduced = motion.reduced
        if fanLayer == nil {
            let descriptor = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .bgra8Unorm,
                width: drawable.texture.width, height: drawable.texture.height, mipmapped: false)
            descriptor.usage = [.renderTarget, .shaderRead]
            descriptor.storageMode = .private
            fanLayer = device!.makeTexture(descriptor: descriptor)
        }
        let layerPass = MTLRenderPassDescriptor()
        layerPass.colorAttachments[0].texture = fanLayer
        layerPass.colorAttachments[0].loadAction = .clear
        layerPass.colorAttachments[0].storeAction = .store
        layerPass.colorAttachments[0].clearColor = MTLClearColorMake(0, 0, 0, 0)
        let fanEncoder = command.makeRenderCommandEncoder(descriptor: layerPass)!
        let progress = reduced ? 0 : u
        if progress * 1900 < 1110 {
            for i in 0..<9 {
                let index = i % 8
                if i == 8 && progress * 1900 >= 505 { continue }
                let values = T.fan(index, motion.spin, progress, motion.anchor, motion.fanPress, seam: i == 8)
                let pose = T.card(index, motion.spin, progress, motion.anchor)
                let angle = T.radians(pose.rotation)
                let c = abs(cos(angle))
                let s = abs(sin(angle))
                let hw = (c * 99 + s * 72.47) * pose.scale + 5
                let hh = (s * 99 + c * 72.47) * pose.scale + 5
                let rect = CGRect(x: pose.x - hw, y: pose.y - hh, width: hw * 2, height: hh * 2)
                paint("FAN_SHADOW", values, rect: rect, texture: shadow, encoder: fanEncoder)
                paint("FAN", values, rect: rect, texture: faces[index], encoder: fanEncoder)
            }
            paint("DARKNESS", [T.vector(reduced ? 1 : 1 - T.phase(u * 1900, 300, 900))],
                  rect: CGRect(x: 0, y: 200, width: 402, height: 460), encoder: fanEncoder)
        }
        if reduced {
            paintHero(T.hero(0, -49, 0, 0, motion.objectPress, 0, 0, reduced: true), encoder: fanEncoder)
        }
        fanEncoder.endEncoding()

        let hero = T.hero(reduced ? 1 : u, motion.spin, motion.clock, reduced ? 0 : motion.forward,
                          motion.objectPress, motion.yaw, motion.pitch, reduced: reduced)
        if reduced && u > 0 {
            if heroLayer == nil {
                let descriptor = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .bgra8Unorm,
                    width: drawable.texture.width, height: drawable.texture.height, mipmapped: false)
                descriptor.usage = [.renderTarget, .shaderRead]
                descriptor.storageMode = .private
                heroLayer = device!.makeTexture(descriptor: descriptor)
            }
            let heroPass = MTLRenderPassDescriptor()
            heroPass.colorAttachments[0].texture = heroLayer
            heroPass.colorAttachments[0].loadAction = .clear
            heroPass.colorAttachments[0].storeAction = .store
            heroPass.colorAttachments[0].clearColor = MTLClearColorMake(0, 0, 0, 0)
            let heroEncoder = command.makeRenderCommandEncoder(descriptor: heroPass)!
            paintHero(hero, encoder: heroEncoder)
            heroEncoder.endEncoding()
        }
        let encoder = command.makeRenderCommandEncoder(descriptor: pass)!
        let page = reduced ? u : T.clamp(T.lift(u * 1900))
        paint("PAGE", [T.vector(page), T.vector(0, 0, 1)], encoder: encoder)
        paint("BOUNCE", T.bounce(u, motion.spin, motion.anchor, reduced: reduced), encoder: encoder)
        if !reduced { paint("BLOOM", hero.bloom, encoder: encoder) }
        paint("COMPOSITE", [T.vector(reduced ? 1 - u : 1)], texture: fanLayer, encoder: encoder)
        if reduced {
            if u > 0 { paint("COMPOSITE", [T.vector(u)], texture: heroLayer, encoder: encoder) }
        } else {
            paintHero(hero, encoder: encoder)
        }
        let halo = u == 0 && !reduced ? 0.035 + 0.019 * sin(motion.clock * .pi * 2 / 2.4) : 0
        if halo > 0 { paint("HALO", [T.vector(halo)], encoder: encoder) }
        for index in 0..<7 {
            let state = T.text(index, u, reduced: reduced)
            if state.alpha < 0.001 { continue }
            let y = [616.0, 650, 326, 360, 256, 806, 806][index] - 30 + state.offset
            let alpha = state.alpha * (index > 4 ? 0.46 : 1)
            paint("TEXT", [T.vector(0, y, state.blur, alpha)],
                  rect: CGRect(x: 0, y: y, width: 402, height: 60), texture: texts[index], encoder: encoder)
        }
        encoder.endEncoding()
        command.present(drawable)
        command.commit()
    }

    private func paintHero(_ hero: T.Hero, opacity: Double = 1, encoder: MTLRenderCommandEncoder) {
        guard opacity > 0 else { return }
        paint("HERO_SHADOW", hero.shadow, opacity: opacity, encoder: encoder)
        paint("INNER_LIGHT", hero.inner, opacity: opacity, encoder: encoder)
        let centre = hero.uniforms[0]
        paint("HERO", hero.uniforms, rect: CGRect(x: Double(centre.x) - 225, y: Double(centre.y) - 180, width: 450, height: 360),
              texture: chip, opacity: opacity, encoder: encoder)
    }

    private func paint(_ effect: String, _ uniforms: [SIMD4<Float>], rect: CGRect = CGRect(x: -100, y: 0, width: 602, height: 874),
                       texture: MTLTexture? = nil, opacity: Double = 1, encoder: MTLRenderCommandEncoder) {
        let geometry = [T.vector(drawableSize.width, drawableSize.height), T.vector(rect.minX, rect.minY, rect.width, rect.height)]
        var alpha = Float(opacity)
        encoder.setRenderPipelineState(pipelines[effect]!)
        encoder.setVertexBytes(geometry, length: geometry.count * MemoryLayout<SIMD4<Float>>.stride, index: 0)
        encoder.setFragmentBytes(uniforms, length: uniforms.count * MemoryLayout<SIMD4<Float>>.stride, index: 0)
        encoder.setFragmentBytes(&alpha, length: MemoryLayout<Float>.size, index: 1)
        if let texture { encoder.setFragmentTexture(texture, index: 0) }
        encoder.drawPrimitives(type: .triangleStrip, vertexStart: 0, vertexCount: 4)
    }

    private func designPoint(_ point: CGPoint) -> CGPoint {
        let scale = min(bounds.width / 402, bounds.height / 874)
        return CGPoint(x: (point.x - (bounds.width - 402 * scale) / 2) / scale,
                       y: (point.y - (bounds.height - 874 * scale) / 2) / scale)
    }

    @objc private func handleTouch(_ recognizer: UILongPressGestureRecognizer) {
        let now = CACurrentMediaTime()
        motion.advance(now)
        let point = designPoint(recognizer.location(in: self))
        switch recognizer.state {
        case .began:
            previousPoint = point
            previousTime = now
            fingerVelocity = .zero
            light.prepare()
            medium.prepare()
            motion.begin(point)
        case .changed:
            let dt = max(now - previousTime, 0.001)
            fingerVelocity = CGPoint(x: (point.x - previousPoint.x) / dt, y: (point.y - previousPoint.y) / dt)
            motion.move(point, velocity: fingerVelocity)
            previousPoint = point
            previousTime = now
        case .ended:
            if now - previousTime > 0.1 { fingerVelocity = .zero }
            motion.move(point, velocity: fingerVelocity)
            motion.end(velocity: fingerVelocity)
        case .cancelled, .failed:
            motion.end(cancelled: true)
        default: break
        }
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

    private func updateAccessibility() {
        guard heading != nil && control != nil else { return }
        heading.accessibilityLabel = sceneHero ? "Meet the first Metacard" : "Unify the limit of all your cards"
        control.accessibilityLabel = sceneHero ? "Split Metacard" : "Unify cards"
        control.accessibilityValue = sceneHero ? "One card" : "Eight cards"
        control.accessibilityHint = sceneHero ? "Fans the eight cards out again" : "Folds the eight cards into one Metacard"
        let scale = min(bounds.width / 402, bounds.height / 874)
        let dx = (bounds.width - 402 * scale) / 2
        let dy = (bounds.height - 874 * scale) / 2
        let rect = sceneHero ? CGRect(x: 40, y: 398, width: 322, height: 245) : CGRect(x: 135, y: 290, width: 132, height: 132)
        control.accessibilityFrameInContainerSpace = CGRect(x: dx + rect.minX * scale, y: dy + rect.minY * scale,
                                                            width: rect.width * scale, height: rect.height * scale)
        heading.accessibilityFrameInContainerSpace = CGRect(x: dx + 30 * scale, y: dy + (sceneHero ? 305 : 595) * scale,
                                                            width: 342 * scale, height: 75 * scale)
    }
}
