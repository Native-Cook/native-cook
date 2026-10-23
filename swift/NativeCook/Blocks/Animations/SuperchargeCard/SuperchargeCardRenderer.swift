import CoreText
import MetalKit
import UIKit

/// The zero-delay recognizer remains simultaneous with the gallery's edge gesture.
final class SuperchargeCardMetalView: MTKView, UIGestureRecognizerDelegate {
    var renderer: SuperchargeCardRenderer!

    init(autoPlay: Bool, reduced: Bool) {
        guard let device = MTLCreateSystemDefaultDevice() else {
            fatalError("Supercharge Card requires Metal")
        }
        super.init(frame: .zero, device: device)
        colorPixelFormat = .bgra8Unorm
        (layer as? CAMetalLayer)?.colorspace = CGColorSpace(name: CGColorSpace.sRGB)
        preferredFramesPerSecond = 60
        isOpaque = true
        backgroundColor = .black
        renderer = SuperchargeCardRenderer(view: self, autoPlay: autoPlay, reduced: reduced)
        delegate = renderer
        let hold = UILongPressGestureRecognizer(target: self, action: #selector(handleHold(_:)))
        hold.minimumPressDuration = 0
        hold.allowableMovement = .greatestFiniteMagnitude
        hold.numberOfTouchesRequired = 1
        hold.cancelsTouchesInView = false
        hold.delegate = self
        addGestureRecognizer(hold)
        isAccessibilityElement = true
        accessibilityTraits = .button
        accessibilityLabel = "Supercharge card"
        accessibilityHint = "Press and hold to charge, then release"
        accessibilityValue = "Empty"
    }

    required init(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    @objc private func handleHold(_ recognizer: UILongPressGestureRecognizer) {
        let location = recognizer.location(in: self)
        let k = min(1, bounds.width / 402, bounds.height / 874)
        let x = (location.x - (bounds.width - 402 * k) / 2) / k
        let y = (location.y - (bounds.height - 874 * k) / 2) / k
        switch recognizer.state {
        case .began: renderer.input("down", x: x, y: y)
        case .changed: renderer.input("move", x: x, y: y)
        case .ended: renderer.input("up")
        case .cancelled, .failed: renderer.input("cancel")
        default: break
        }
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        true
    }

    override func accessibilityActivate() -> Bool {
        renderer.input("activate")
        return true
    }
}

/// Metal renders the material; Core Graphics rasterizes measured glyphs and held electrical paths.
/// A native Swift value model owns the deterministic 240 Hz simulation.
final class SuperchargeCardRenderer: NSObject, MTKViewDelegate {
    var autoPlay: Bool
    var reduced: Bool
    private let queue: MTLCommandQueue
    private let pipeline: MTLRenderPipelineState
    private let paper: MTLTexture
    private let tree: MTLTexture
    private let overlays: [SuperchargeCardOverlay]
    private let inFlight = DispatchSemaphore(value: 3)
    private var index = 0
    private var previousTime: CFTimeInterval?
    private var appearanceTime = 0.0
    private var state: SuperchargeCardFrame
    private var captionFrom = 0
    private var captionTo = 0
    private var captionAt = 0.0
    private var dotAt = Array(repeating: -1.0, count: 12)
    private var reportedPercent = -1
    private weak var view: MTKView?
    private let selection = UISelectionFeedbackGenerator()
    private let notification = UINotificationFeedbackGenerator()
    private let impacts: [(Int, UIImpactFeedbackGenerator)] = [
        (2, UIImpactFeedbackGenerator(style: .light)),
        (4, UIImpactFeedbackGenerator(style: .medium)),
        (8, UIImpactFeedbackGenerator(style: .rigid)),
        (16, UIImpactFeedbackGenerator(style: .soft)),
        (32, UIImpactFeedbackGenerator(style: .heavy))
    ]

    init(view: MTKView, autoPlay: Bool, reduced: Bool) {
        self.view = view
        self.autoPlay = autoPlay
        self.reduced = reduced
        let device = view.device!
        queue = device.makeCommandQueue()!
        let descriptor = MTLRenderPipelineDescriptor()
        let library = device.makeDefaultLibrary()!
        descriptor.vertexFunction = library.makeFunction(name: "superchargeCardVertex")
        descriptor.fragmentFunction = library.makeFunction(name: "superchargeCardFragment")
        descriptor.colorAttachments[0].pixelFormat = view.colorPixelFormat
        pipeline = try! device.makeRenderPipelineState(descriptor: descriptor)
        paper = Self.dataTexture(device: device, name: "paper", width: 2592, height: 576)
        tree = Self.dataTexture(device: device, name: "tree", width: 1632, height: 668)
        overlays = (0..<3).map { _ in SuperchargeCardOverlay(device: device) }
        state = SuperchargeCardState()
        super.init()
    }

    private static func dataTexture(device: MTLDevice, name: String, width: Int, height: Int) -> MTLTexture {
        // Exact L8 PNG samples expanded offline, with no color conversion or premultiplication.
        let url = Bundle.main.url(forResource: "SuperchargeCard-\(name)", withExtension: "bin")!
        let data = try! Data(contentsOf: url)
        precondition(data.count == width * height)
        let descriptor = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .r8Unorm, width: width, height: height, mipmapped: false)
        descriptor.usage = .shaderRead
        let texture = device.makeTexture(descriptor: descriptor)!
        data.withUnsafeBytes { bytes in
            texture.replace(region: MTLRegionMake2D(0, 0, width, height), mipmapLevel: 0, withBytes: bytes.baseAddress!, bytesPerRow: width)
        }
        return texture
    }

    func resetFrameTime() {
        previousTime = nil
    }

    func input(_ type: String, x: Double = 201, y: Double = 392) {
        let action: SuperchargeCardAction
        switch type {
        case "down": action = .down(x: x, y: y)
        case "move": action = .move(x: x, y: y)
        case "up": action = .up
        case "cancel": action = .cancel
        case "activate": action = .activate
        default: return
        }
        SuperchargeCardSimulation.act(
            &state,
            action: action,
            configuration: SuperchargeCardConfiguration(reduced: reduced, autoPlay: autoPlay)
        )
        publish()
    }

    private func publish() {
        let events = state.events
        if events & 1 != 0 { selection.selectionChanged() }
        for (mask, generator) in impacts where events & mask != 0 { generator.impactOccurred() }
        if events & 64 != 0 { notification.notificationOccurred(.success) }
        let percent = Int((state.number("energy") * 100).rounded())
        if percent != reportedPercent {
            reportedPercent = percent
            view?.accessibilityValue = percent == 0 ? "Empty" : percent == 100 ? "Fully charged" : "\(percent) percent"
        }
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

    func draw(in view: MTKView) {
        guard view.bounds.width > 0, let drawable = view.currentDrawable,
              let pass = view.currentRenderPassDescriptor else { return }
        inFlight.wait()
        let now = CACurrentMediaTime()
        let dt = previousTime.map { now - $0 } ?? 0
        previousTime = now
        appearanceTime += min(dt, 1.0 / 30)
        SuperchargeCardSimulation.step(
            &state,
            seconds: dt,
            configuration: SuperchargeCardConfiguration(reduced: reduced, autoPlay: autoPlay)
        )
        publish()
        let caption = Int(state.number("caption"))
        if caption != captionTo || state.clock < captionAt {
            captionFrom = captionTo
            captionTo = caption
            captionAt = state.clock
        }
        for dot in 0..<12 {
            let lit = state.number("meter") + 1e-9 >= Double(dot + 1) / 12
            if lit && dotAt[dot] < 0 { dotAt[dot] = state.clock }
            if !lit { dotAt[dot] = -1 }
        }
        let overlay = overlays[index]
        index = (index + 1) % overlays.count
        overlay.draw(state, captionFrom: captionFrom, captionTo: captionTo, captionAt: captionAt, dotAt: dotAt)
        let size = view.bounds.size
        let k = min(1, size.width / 402, size.height / 874)
        var uniforms: [Float] = [Float(size.width), Float(size.height), Float((size.width - 402 * k) / 2), Float((size.height - 874 * k) / 2), Float(k), Float(state.clock)]
        uniforms += state.card.map(Float.init)
        for key in ["sink", "energy", "glow", "glyph", "glyphScale", "ring", "ringLit", "dim", "tree", "treeGlow", "pulse", "shock"] {
            uniforms.append(Float(state.number(key)))
        }
        uniforms += state.array("shaft").map(Float.init)
        uniforms += state.array("touch").map(Float.init)
        uniforms.append(Float(hypot(state.card[0], state.card[1])))
        uniforms.append(Float(state.number("flash")))
        uniforms.append(Float(min(1, appearanceTime / 0.26)))
        let command = queue.makeCommandBuffer()!
        let encoder = command.makeRenderCommandEncoder(descriptor: pass)!
        encoder.setRenderPipelineState(pipeline)
        encoder.setFragmentBytes(&uniforms, length: uniforms.count * MemoryLayout<Float>.stride, index: 0)
        encoder.setFragmentTexture(paper, index: 0)
        encoder.setFragmentTexture(tree, index: 1)
        encoder.setFragmentTexture(overlay.inkTexture, index: 2)
        encoder.setFragmentTexture(overlay.lightTexture, index: 3)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
        encoder.endEncoding()
        let semaphore = inFlight
        command.addCompletedHandler { _ in semaphore.signal() }
        command.present(drawable)
        command.commit()
    }
}
