import CoreText
import ImageIO
import Metal
import MetalKit
import QuartzCore
import UIKit

/// Metal owns the materials; this view owns the single display clock and input recognizer.
final class FoilPackTearView: UIView, MTKViewDelegate, UIGestureRecognizerDelegate {
    typealias Motion = FoilPackTearMotion

    let metalView: MTKView
    var engine: Motion.Engine
    var autoPlay: Bool
    private let commandQueue: MTLCommandQueue
    private let pipeline: MTLRenderPipelineState
    private let textures: [MTLTexture]
    private let hints = [UILabel(), UILabel(), UILabel()]
    private let captions = ["DRAG ACROSS THE TOP TO TEAR", "PULL THE CARD OUT", "TAP THE CARD TO COLLECT"]
    private var lastTime: CFTimeInterval?
    private var sceneClock = 0.0
    private var entranceAge = 0.0
    private var currentHint = -1
    private var hintChanged = 0.0
    private var hintFrom = [Double](repeating: 0, count: 3)
    private var hintAlpha = [Double](repeating: 0, count: 3)
    private var phase = ""
    private var lastPoint = CGPoint.zero
    private var lastTouchTime = 0.0
    private var touchVelocity = CGPoint.zero
    private let selection = UISelectionFeedbackGenerator()
    private let success = UINotificationFeedbackGenerator()
    private let impact = UIImpactFeedbackGenerator(style: .light)
    private let mediumImpact = UIImpactFeedbackGenerator(style: .medium)
    private let softImpact = UIImpactFeedbackGenerator(style: .soft)

    init(autoPlay: Bool, reduced: Bool) {
        guard let device = MTLCreateSystemDefaultDevice(), let queue = device.makeCommandQueue() else {
            preconditionFailure("Foil Pack Tear requires Metal")
        }
        self.autoPlay = autoPlay
        engine = Motion.initialEngine(reduced)
        commandQueue = queue
        metalView = MTKView(frame: .zero, device: device)
        metalView.colorPixelFormat = .bgra8Unorm
        metalView.framebufferOnly = true
        metalView.preferredFramesPerSecond = 60
        metalView.isOpaque = true
        metalView.clearColor = MTLClearColor(red: 1 / 255, green: 2 / 255, blue: 7 / 255, alpha: 1)
        // Match the original Skia surface: shader constants are in Display P3.
        (metalView.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name: CGColorSpace.displayP3)
        do {
            let library = device.makeDefaultLibrary()!
            let descriptor = MTLRenderPipelineDescriptor()
            descriptor.vertexFunction = library.makeFunction(name: "foilPackTearVertex")
            descriptor.fragmentFunction = library.makeFunction(name: "foilPackTearFragment")
            descriptor.colorAttachments[0].pixelFormat = .bgra8Unorm
            pipeline = try device.makeRenderPipelineState(descriptor: descriptor)
            textures = try ["pack-maps", "pack-print", "card-face", "card-back", "card-maps", "tab-label", "spark"].map {
                try Self.texture($0, device: device)
            }
        } catch {
            preconditionFailure("Foil Pack Tear resources: \(error)")
        }
        super.init(frame: .zero)
        backgroundColor = UIColor(red: 1 / 255, green: 2 / 255, blue: 7 / 255, alpha: 1)
        addSubview(metalView)
        metalView.isUserInteractionEnabled = false
        metalView.delegate = self
        Self.registerFont()
        for (i, label) in hints.enumerated() {
            label.text = captions[i]
            label.textAlignment = .center
            label.isAccessibilityElement = false
            label.isUserInteractionEnabled = false
            label.alpha = 0
            addSubview(label)
        }
        let touch = UILongPressGestureRecognizer(target: self, action: #selector(handleTouch(_:)))
        touch.minimumPressDuration = 0
        touch.allowableMovement = .greatestFiniteMagnitude
        touch.numberOfTouchesRequired = 1
        touch.cancelsTouchesInView = false
        touch.delegate = self
        addGestureRecognizer(touch)
        isAccessibilityElement = true
        accessibilityTraits = .button
        updateAccessibility()
    }

    required init?(coder: NSCoder) { nil }

    func setActive(_ active: Bool) {
        if metalView.isPaused == active {
            metalView.isPaused = !active
            lastTime = nil
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        metalView.frame = bounds
        let fit = min(bounds.width / 402, bounds.height / 874)
        let textScale = max(fit, 0.9)
        for (i, label) in hints.enumerated() {
            label.attributedText = NSAttributedString(string: captions[i], attributes: [
                .font: UIFont(name: "ArchivoExpanded-Regular", size: 11 * textScale) ?? UIFont.systemFont(ofSize: 11 * textScale),
                .kern: 1.4,
                .foregroundColor: UIColor(red: 196 / 255, green: 214 / 255, blue: 1, alpha: 0.56)
            ])
            label.frame = CGRect(x: 8, y: (bounds.height - 874 * fit) / 2 + 806 * fit - 9 * textScale,
                                 width: bounds.width - 16, height: 18 * textScale)
        }
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        true
    }

    private func stagePoint(_ point: CGPoint) -> CGPoint {
        let fit = max(0.001, min(bounds.width / 402, bounds.height / 874))
        return CGPoint(x: (point.x - (bounds.width - 402 * fit) / 2) / fit,
                       y: (point.y - (bounds.height - 874 * fit) / 2) / fit)
    }

    @objc private func handleTouch(_ recognizer: UILongPressGestureRecognizer) {
        let point = stagePoint(recognizer.location(in: self))
        let now = CACurrentMediaTime()
        switch recognizer.state {
        case .began:
            lastPoint = point
            lastTouchTime = now
            touchVelocity = .zero
            selection.prepare()
            impact.prepare()
            act(.init(type: "down", x: point.x, y: point.y))
        case .changed:
            let dt = max(0.001, now - lastTouchTime)
            touchVelocity = CGPoint(x: (point.x - lastPoint.x) / dt, y: (point.y - lastPoint.y) / dt)
            lastPoint = point
            lastTouchTime = now
            act(.init(type: "move", x: point.x, y: point.y, vx: touchVelocity.x, vy: touchVelocity.y))
        case .ended, .cancelled, .failed:
            if now - lastTouchTime > 0.08 { touchVelocity = .zero }
            act(.init(type: "up", vx: touchVelocity.x, vy: touchVelocity.y, cancelled: recognizer.state != .ended))
        default:
            break
        }
    }

    func act(_ input: Motion.Input) {
        engine = Motion.actEngine(engine, input)
        playEvents()
        updateAccessibility()
    }

    override func accessibilityActivate() -> Bool {
        act(.init(type: "activate"))
        return true
    }

    private func playEvents() {
        for event in engine.sim.events {
            switch event {
            case Motion.HAPTIC.tooth: selection.selectionChanged()
            case Motion.HAPTIC.face: success.notificationOccurred(.success)
            case Motion.HAPTIC.open: mediumImpact.impactOccurred()
            case Motion.HAPTIC.peek, Motion.HAPTIC.land: softImpact.impactOccurred()
            default: impact.impactOccurred()
            }
        }
    }

    private func updateAccessibility() {
        let next = engine.sim.phase
        if next != phase {
            phase = next
            switch next {
            case "sealed", "tearing":
                accessibilityLabel = next == "sealed" ? "Sealed foil pack" : "Partially torn foil pack"
                accessibilityHint = "Double tap to tear it open"
            case "peek":
                accessibilityLabel = "Card peeking out of the pack"
                accessibilityHint = "Double tap to pull it out"
            case "revealed":
                accessibilityLabel = "Solstice Koi, legendary card"
                accessibilityHint = "Double tap to collect it"
            default:
                accessibilityLabel = next == "collecting" ? "Collecting Solstice Koi" : next == "opened" ? "Opening foil pack" : "Revealing Solstice Koi"
                accessibilityHint = "Wait for the animation to finish"
            }
        }
        accessibilityValue = next == "tearing" ? "\(Int(engine.sim.pose.tearFront / 216 * 100)) percent torn" : next
    }

    private func updateHints() {
        let next = phase == "sealed" || phase == "tearing" ? 0 : phase == "peek" ? 1 : phase == "revealed" ? 2 : -1
        if next != currentHint {
            hintFrom = hintAlpha
            hintChanged = entranceAge
            currentHint = next
        }
        let age = entranceAge - hintChanged
        for i in 0..<3 {
            let active = i == currentHint
            let progress = Motion.ease((age - (active ? 0.16 : 0)) / (active ? 0.24 : 0.16))
            hintAlpha[i] = active ? progress : hintFrom[i] * (1 - progress)
            hints[i].alpha = hintAlpha[i] * min(1, entranceAge / 0.22)
            let shift = engine.sim.reduced ? 0 : active ? 6 * (1 - progress) : -6 * progress
            hints[i].transform = CGAffineTransform(translationX: 0, y: shift)
        }
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

    func draw(in view: MTKView) {
        guard window != nil, let drawable = view.currentDrawable,
              let pass = view.currentRenderPassDescriptor, let command = commandQueue.makeCommandBuffer() else { return }
        let now = CACurrentMediaTime()
        let elapsed = lastTime.map { min(0.1, max(0, now - $0)) } ?? 0
        lastTime = now
        entranceAge += elapsed
        if !engine.sim.reduced { sceneClock = (sceneClock + elapsed).truncatingRemainder(dividingBy: 13) }
        engine = Motion.stepEngine(engine, elapsed, autoPlay)
        playEvents()
        updateAccessibility()
        updateHints()
        metalView.alpha = min(1, entranceAge / 0.22)
        var uniforms = shaderUniforms()
        guard let encoder = command.makeRenderCommandEncoder(descriptor: pass) else { return }
        encoder.setRenderPipelineState(pipeline)
        encoder.setFragmentBytes(&uniforms, length: uniforms.count * MemoryLayout<Float>.size, index: 0)
        for (i, texture) in textures.enumerated() { encoder.setFragmentTexture(texture, index: i) }
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
        encoder.endEncoding()
        command.present(drawable)
        command.commit()
    }

    private static func registerFont() {
        _ = registeredFont
    }

    private static let registeredFont: Void = {
        if let url = Bundle.main.url(forResource: "FoilPackTear-ArchivoExpanded-Regular", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    private static func texture(_ name: String, device: MTLDevice) throws -> MTLTexture {
        guard let url = Bundle.main.url(forResource: "FoilPackTear-" + name, withExtension: "png"),
              let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
            throw NSError(domain: "FoilPackTear", code: 1)
        }
        let dataMap = name.hasSuffix("maps")
        let descriptor = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: dataMap ? .r8Unorm : .rgba8Unorm,
                                                                   width: image.width, height: image.height, mipmapped: false)
        descriptor.usage = .shaderRead
        guard let texture = device.makeTexture(descriptor: descriptor) else {
            throw NSError(domain: "FoilPackTear", code: 2)
        }
        if dataMap {
            // Do not draw grayscale data through a colour-managed CGContext.
            guard image.bitsPerPixel == 8, let data = image.dataProvider?.data, let bytes = CFDataGetBytePtr(data) else {
                throw NSError(domain: "FoilPackTear", code: 3)
            }
            texture.replace(region: MTLRegionMake2D(0, 0, image.width, image.height), mipmapLevel: 0,
                            withBytes: bytes, bytesPerRow: image.bytesPerRow)
        } else {
            var bytes = [UInt8](repeating: 0, count: image.width * image.height * 4)
            let space = CGColorSpace(name: CGColorSpace.displayP3)!
            bytes.withUnsafeMutableBytes { buffer in
                let context = CGContext(data: buffer.baseAddress, width: image.width, height: image.height,
                                        bitsPerComponent: 8, bytesPerRow: image.width * 4, space: space,
                                        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
                context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
                texture.replace(region: MTLRegionMake2D(0, 0, image.width, image.height), mipmapLevel: 0,
                                withBytes: buffer.baseAddress!, bytesPerRow: image.width * 4)
            }
        }
        return texture
    }
}

extension FoilPackTearView {
    private func shaderUniforms() -> [Float] {
        let p = engine.sim.pose
        var values: [Float] = [Float(bounds.width), Float(bounds.height), Float(engine.sim.reduced ? 0 : sceneClock), engine.sim.reduced ? 1 : 0]
        values.append(Float(p.lightX))
        values.append(Float(p.lightY))
        values.append(Float(p.swayX))
        values.append(Float(p.swayY))
        values.append(Float(p.calm))
        values.append(Float(p.packX))
        values.append(Float(p.packY))
        values.append(Float(p.packRot))
        values.append(Float(p.packScale))
        values.append(Float(p.packOpacity))
        values.append(Float(p.tearFront))
        values.append(Float(p.stripFree))
        values.append(Float(p.stripX))
        values.append(Float(p.stripY))
        values.append(Float(p.stripRot))
        values.append(Float(p.stripOpacity))
        values.append(Float(p.leak))
        values.append(Float(p.burst))
        values.append(Float(p.mouth))
        values.append(Float(p.cardX))
        values.append(Float(p.cardY))
        values.append(Float(p.cardScale))
        values.append(Float(p.cardFlip))
        values.append(Float(p.cardTiltX))
        values.append(Float(p.cardTiltY))
        values.append(Float(p.cardOpacity))
        values.append(Float(p.sheen))
        values.append(Float(p.frameHeat))
        values.append(Float(p.frameRing))
        values.append(Float(p.tabGlow))
        values.append(Float(p.sparkleAge))
        values.append(Float(p.dust))
        values.append(contentsOf: p.tearLine.map(Float.init))
        for i in 0..<66 {
            let burst = i < 48
            let seed = Double(burst ? i : i - 48 + 100)
            func seedValue(_ value: Double) -> Double {
                let result = sin(value * 127.1 + 311.7) * 43758.5453
                return result - floor(result)
            }
            let a = seedValue(seed + 1)
            let b = seedValue(seed + 23)
            let c = seedValue(seed + 87)
            var x, y, size, rotation, alpha: Double
            if burst {
                let age = max(0, p.sparkleAge)
                let travel = (1 - exp(-age * 5.2)) * (175 + b * 135)
                let theta = a * Double.pi * 2
                x = p.cardX + cos(theta) * travel
                y = p.cardY + sin(theta) * travel + age * age * 42
                size = (8 + c * 14) * (0.88 + 0.12 * sin(age * 13 + a * 20))
                rotation = a * Double.pi + age * (b - 0.5) * 1.2
                alpha = p.sparkleAge < 0 ? 0 : max(0, 1 - age / 1.6) * (0.65 + 0.55 * pow(sin(age * 16 + b * 30), 2))
            } else {
                let cycle = (engine.sim.reduced ? 0 : sceneClock) / 13
                let phase = (cycle * (1 + floor(b * 2)) + a).truncatingRemainder(dividingBy: 1)
                let pulse = cycle * Double.pi * 8 + b * 50
                let side: Double = b < 0.5 ? -1 : 1
                let peekX = 201 + p.packX + side * (98 + c * 15 + phase * 21)
                x = peekX * (1 - p.frameHeat) + (p.cardX + side * (132 + c * 24)) * p.frameHeat + sin(phase * Double.pi * 2 + c * 20) * 4
                y = (176 + p.packY + 34 - 6 - phase * 122) * (1 - p.frameHeat) + (p.cardY + 252 * 0.7 - phase * 370) * p.frameHeat
                size = 3 + c * 3 + 0.5 + 0.5 * sin(pulse + 1.7)
                rotation = a * Double.pi + sin(cycle * Double.pi * 2 + b * 12) * 0.7
                alpha = p.dust * sin(phase * Double.pi) * (0.40 + 0.55 * pow(sin(pulse), 2))
            }
            values.append(contentsOf: [x, y, size, rotation, 0, alpha].map(Float.init))
        }
        return values
    }
}
