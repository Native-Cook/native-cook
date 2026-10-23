import CoreText
import MetalKit
import SwiftUI
import UIKit

struct GlassBladeDrawSurface: UIViewRepresentable {
    let autoPlay: Bool
    let reduced: Bool

    func makeUIView(context: Context) -> GlassBladeDrawScreen {
        let view = GlassBladeDrawScreen()
        view.motion.configure(autoPlay: autoPlay, reduced: reduced)
        return view
    }

    func updateUIView(_ view: GlassBladeDrawScreen, context: Context) {
        view.motion.configure(autoPlay: autoPlay, reduced: reduced)
    }

    static func dismantleUIView(_ view: GlassBladeDrawScreen, coordinator: ()) {
        view.stop()
    }
}

enum GlassBladeDrawAssets {
    static let registered: Void = {
        for weight in ["Regular", "Bold"] {
            if let url = Bundle.main.url(forResource: "GlassBladeDraw-Inter-\(weight)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
}

final class GlassBladeDrawAccessibleFan: UIView {
    var step: ((Int) -> Void)?
    override func accessibilityIncrement() { step?(1) }
    override func accessibilityDecrement() { step?(-1) }
}

final class GlassBladeDrawAccessibleBar: UIView {
    var activate: (() -> Void)?
    override func accessibilityActivate() -> Bool {
        activate?()
        return true
    }
}

/// UIKit hosts the zero-delay recognizers so the gallery's edge gesture cannot cancel a drag.
final class GlassBladeDrawScreen: UIView, MTKViewDelegate, UIGestureRecognizerDelegate {
    let motion = GlassBladeDrawMotion()
    private let metalView: MTKView
    private let commandQueue: MTLCommandQueue?
    private var pipeline: MTLRenderPipelineState?
    private var atlas: MTLTexture?
    private let mark = UIImageView()
    private let wordmark = UILabel()
    private let headline = UILabel()
    private let subtitle = UILabel()
    private let barLabel = UILabel()
    private let fan = GlassBladeDrawAccessibleFan()
    private let bar = GlassBladeDrawAccessibleBar()
    private var previousTime: CFTimeInterval?
    private var touchOrigin = CGPoint.zero
    private var previousTouch = CGPoint.zero
    private var previousTouchTime = 0.0
    private let lightImpact = UIImpactFeedbackGenerator(style: .light)
    private let landingImpact = UIImpactFeedbackGenerator(style: .medium)
    private let selection = UISelectionFeedbackGenerator()
    private var layout = GlassBladeDrawLayout(width: 402, height: 874, bottomInset: 34)

    override init(frame: CGRect) {
        let device = MTLCreateSystemDefaultDevice()
        metalView = MTKView(frame: .zero, device: device)
        commandQueue = device?.makeCommandQueue()
        super.init(frame: frame)
        backgroundColor = .black
        clipsToBounds = true
        _ = GlassBladeDrawAssets.registered
        metalView.colorPixelFormat = .bgra8Unorm
        metalView.clearColor = MTLClearColorMake(0, 0, 0, 1)
        metalView.framebufferOnly = true
        metalView.preferredFramesPerSecond = 60
        metalView.isUserInteractionEnabled = false
        // Shader values are the source's display-encoded RGB, written without an sRGB conversion.
        (metalView.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name: CGColorSpace.sRGB)
        addSubview(metalView)
        if let device {
            do {
                let library = device.makeDefaultLibrary()
                let descriptor = MTLRenderPipelineDescriptor()
                descriptor.vertexFunction = library?.makeFunction(name: "glassBladeDrawVertex")
                descriptor.fragmentFunction = library?.makeFunction(name: "glassBladeDrawFragment")
                descriptor.colorAttachments[0].pixelFormat = metalView.colorPixelFormat
                pipeline = try device.makeRenderPipelineState(descriptor: descriptor)
                guard let url = Bundle.main.url(forResource: "GlassBladeDraw-blade-atlas", withExtension: "png") else {
                    throw NSError(domain: "GlassBladeDraw", code: 1, userInfo: [NSLocalizedDescriptionKey: "Missing blade atlas"])
                }
                atlas = try MTKTextureLoader(device: device).newTexture(URL: url, options: [
                    .SRGB: false, .origin: MTKTextureLoader.Origin.topLeft,
                    .generateMipmaps: false, .textureUsage: MTLTextureUsage.shaderRead.rawValue
                ])
            } catch {
                assertionFailure("GlassBladeDraw renderer: \(error)")
            }
        }
        mark.image = UIImage(named: "GlassBladeDraw-vane-mark")
        mark.contentMode = .scaleAspectFit
        mark.isAccessibilityElement = true
        mark.accessibilityLabel = "Vane"
        addSubview(mark)
        for label in [wordmark, headline, subtitle, barLabel] {
            label.textAlignment = .center
            label.isUserInteractionEnabled = false
            addSubview(label)
        }
        headline.numberOfLines = 2
        headline.accessibilityTraits = .header
        barLabel.isAccessibilityElement = false
        fan.isAccessibilityElement = true
        fan.accessibilityLabel = "Blades"
        fan.accessibilityTraits = .adjustable
        fan.step = { [weak self] in self?.motion.accessibilityStep($0) }
        addSubview(fan)
        bar.isAccessibilityElement = true
        bar.accessibilityLabel = "Draw a blade"
        bar.accessibilityTraits = .button
        bar.activate = { [weak self] in self?.motion.accessibilityDraw() }
        addSubview(bar)
        for (view, action) in [(fan, #selector(fanTouch(_:))), (bar, #selector(barTouch(_:)))] {
            let gesture = UILongPressGestureRecognizer(target: self, action: action)
            gesture.minimumPressDuration = 0
            gesture.allowableMovement = .greatestFiniteMagnitude
            gesture.numberOfTouchesRequired = 1
            gesture.delegate = self
            view.addGestureRecognizer(gesture)
        }
        motion.onSelection = { [weak self] in self?.selection.selectionChanged() }
        motion.onImpact = { [weak self] landed in
            guard let self else { return }
            if landed { landingImpact.impactOccurred() } else { lightImpact.impactOccurred() }
        }
        metalView.delegate = self
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func stop() {
        metalView.isPaused = true
        metalView.delegate = nil
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        previousTime = nil
        metalView.isPaused = window == nil
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        guard bounds.width > 0, bounds.height > 0 else { return }
        let safe = window?.safeAreaInsets ?? safeAreaInsets
        layout = GlassBladeDrawLayout(width: bounds.width, height: bounds.height, bottomInset: safe.bottom)
        metalView.frame = bounds
        let scale = window?.screen.scale ?? traitCollection.displayScale
        metalView.contentScaleFactor = scale
        metalView.drawableSize = CGSize(width: bounds.width * scale, height: bounds.height * scale)
        let top = max(safe.top, 20) + 12
        let compact = bounds.width < 390
        let fontSize = compact ? 28.0 : 30.0
        let lineHeight = compact ? 32.0 : 34.0
        mark.frame = CGRect(x: bounds.midX - 14, y: top, width: 28, height: 28)
        wordmark.frame = CGRect(x: 24, y: top + 36, width: bounds.width - 48, height: 16)
        headline.frame = CGRect(x: 24, y: top + 76, width: bounds.width - 48, height: lineHeight * 2)
        subtitle.frame = CGRect(x: 24, y: headline.frame.maxY + 16, width: bounds.width - 48, height: 18)
        text(wordmark, "vane", size: 12, height: 16, tracking: 0.4, alpha: 0.72)
        text(headline, "Nine blades. One lights.\nNobody loses.", size: fontSize, height: lineHeight, tracking: -1)
        text(subtitle, "Every stake comes back. Only the prize moves.", size: 13, height: 18, alpha: 0.62)
        bar.frame = CGRect(x: 16, y: layout.ctaY, width: layout.ctaW, height: 56)
        barLabel.frame = bar.frame
        text(barLabel, "Draw a blade", size: 16, height: 20, tracking: -0.2, bold: true)
        fan.frame = CGRect(x: 0, y: layout.ctaY - 8 - 300 * layout.scale,
                           width: layout.width, height: 300 * layout.scale)
    }

    private func text(_ label: UILabel, _ string: String, size: Double, height: Double,
                      tracking: Double = 0, alpha: Double = 1, bold: Bool = false) {
        let font = UIFont(name: bold ? "Inter-Bold" : "Inter-Regular", size: size)
            ?? UIFont.systemFont(ofSize: size, weight: bold ? .bold : .regular)
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = .center
        paragraph.minimumLineHeight = height
        paragraph.maximumLineHeight = height
        var attributes: [NSAttributedString.Key: Any] = [
            .font: font, .kern: tracking, .paragraphStyle: paragraph,
            .baselineOffset: (height - font.lineHeight) / 2
        ]
        if label !== barLabel {
            attributes[.foregroundColor] = UIColor(red: 246 / 255, green: 247 / 255, blue: 242 / 255, alpha: alpha)
        }
        label.attributedText = NSAttributedString(string: string, attributes: attributes)
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

    func draw(in view: MTKView) {
        guard let pipeline, let atlas, let commandQueue,
              let pass = view.currentRenderPassDescriptor, let drawable = view.currentDrawable,
              let command = commandQueue.makeCommandBuffer(),
              let encoder = command.makeRenderCommandEncoder(descriptor: pass) else { return }
        let now = CACurrentMediaTime()
        motion.advance(previousTime.map { now - $0 } ?? 0)
        previousTime = now
        let state = motion.frame
        let uniforms = GlassBladeDrawModel.uniforms(layout, state, seconds: motion.seconds,
                                                    displayScale: view.drawableSize.width / max(1, bounds.width))
        encoder.setRenderPipelineState(pipeline)
        uniforms.withUnsafeBytes { bytes in
            encoder.setFragmentBytes(bytes.baseAddress!, length: bytes.count, index: 0)
        }
        encoder.setFragmentTexture(atlas, index: 0)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
        encoder.endEncoding()
        command.present(drawable)
        command.commit()
        for view in [mark, wordmark, headline, subtitle, barLabel] { view.alpha = motion.opacity }
        barLabel.transform = CGAffineTransform(scaleX: 1 - 0.03 * state.press, y: 1 - 0.03 * state.press)
        let fraction = GlassBladeDrawTimeline.clamp((state.ctaEnergy - 0.25) / 0.35)
        let dark = [230.0 / 255, 1.0, 106.0 / 255]
        let lit = [11.0 / 255, 12.0 / 255, 2.0 / 255]
        let channels = zip(dark, lit).map { pow(pow($0, 2.2) * (1 - fraction) + pow($1, 2.2) * fraction, 1 / 2.2) }
        let labelColour = UIColor(red: channels[0], green: channels[1], blue: channels[2], alpha: 1)
        barLabel.textColor = labelColour
        let index = GlassBladeDrawModel.wrapped(Int(GlassBladeDrawModel.rounded(state.pos)))
        fan.accessibilityValue = "\(GlassBladeDrawModel.values[index]), \(GlassBladeDrawModel.hues[index % 3])"
        bar.accessibilityValue = motion.drawing ? "Drawing" : "Ready"
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

    @objc private func fanTouch(_ gesture: UILongPressGestureRecognizer) {
        let point = gesture.location(in: self)
        let now = CACurrentMediaTime()
        switch gesture.state {
        case .began:
            motion.beginFan()
            touchOrigin = point
            previousTouch = point
            previousTouchTime = now
        case .changed:
            let dt = max(0.001, now - previousTouchTime)
            motion.moveFan(x: point.x - touchOrigin.x, y: point.y - touchOrigin.y,
                           velocity: (point.x - previousTouch.x) / dt, scale: layout.scale)
            previousTouch = point
            previousTouchTime = now
        case .ended, .cancelled, .failed:
            motion.endFan(x: point.x, width: layout.width, scale: layout.scale, cancelled: gesture.state != .ended)
        default: break
        }
    }

    @objc private func barTouch(_ gesture: UILongPressGestureRecognizer) {
        switch gesture.state {
        case .began: motion.beginBar()
        case .changed:
            if !bar.bounds.contains(gesture.location(in: bar)) { motion.endBar(cancelled: true) }
        case .ended: motion.endBar(cancelled: !bar.bounds.contains(gesture.location(in: bar)))
        case .cancelled, .failed: motion.endBar(cancelled: true)
        default: break
        }
    }
}
