import SwiftUI
import MetalKit
import CoreText
import ImageIO

struct HoneyDrizzleStepper: View {
    var autoPlay = false
    static let loop = 6.0
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scene
    var body: some View {
        HoneyDrizzleStepperSurface(autoPlay: autoPlay, reduced: reduced, active: scene == .active)
            .ignoresSafeArea().background(Color(red: 242/255, green: 106/255, blue: 27/255))
    }
}
private struct HoneyDrizzleStepperSurface: UIViewRepresentable {
    var autoPlay: Bool, reduced: Bool, active: Bool
    func makeUIView(context: Context) -> HoneyDrizzleStepperView { HoneyDrizzleStepperView() }
    func updateUIView(_ v: HoneyDrizzleStepperView, context: Context) {
        v.renderer.autoPlay = autoPlay
        v.renderer.core.setStill(reduced)
        v.active = active; v.refreshClock()
    }
}
private final class HoneyDrizzleStepperRenderer: NSObject, MTKViewDelegate {
    let core = HoneyDrizzleStepperMotion()
    var autoPlay = false
    var onFrame: ((HoneyDrizzleStepperMotion.Frame) -> Void)?
    private let queue: MTLCommandQueue, pipeline: MTLRenderPipelineState, photo: MTLTexture
    private let inFlight = DispatchSemaphore(value: 3)
    struct Uniforms { var clock: SIMD4<Float>; var layout: SIMD4<Float> }
    init(_ device: MTLDevice) {
        queue = device.makeCommandQueue()!
        let library = device.makeDefaultLibrary()!, desc = MTLRenderPipelineDescriptor()
        desc.vertexFunction = library.makeFunction(name: "HoneyDrizzleStepperVertex")
        desc.fragmentFunction = library.makeFunction(name: "HoneyDrizzleStepperFragment")
        desc.colorAttachments[0].pixelFormat = .rgba16Float
        pipeline = try! device.makeRenderPipelineState(descriptor: desc)
        let url = Bundle.main.url(forResource: "HoneyDrizzleStepper-honey", withExtension: "webp")!
        let source = CGImageSourceCreateWithURL(url as CFURL, nil)!
        let image = CGImageSourceCreateImageAtIndex(source, 0, nil)!
        let w = image.width, h = image.height
        var pixels = [UInt8](repeating: 0, count: w*h*4)
        let cg = CGContext(data: &pixels, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w*4,
                           space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        cg.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))
        photo = device.makeTexture(descriptor: .texture2DDescriptor(pixelFormat: .rgba8Unorm, width: w, height: h, mipmapped: false))!
        photo.replace(region: MTLRegionMake2D(0, 0, w, h), mipmapLevel: 0, withBytes: pixels, bytesPerRow: w*4)
        super.init()
    }
    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}
    func draw(in view: MTKView) {
        guard view.window != nil, view.bounds.width > 0, inFlight.wait(timeout: .now()) == .success else { return }
        guard let drawable = view.currentDrawable, let pass = view.currentRenderPassDescriptor,
              let command = queue.makeCommandBuffer(), let enc = command.makeRenderCommandEncoder(descriptor: pass) else { inFlight.signal(); return }
        core.step(CACurrentMediaTime(), autoPlay: autoPlay)
        let f = core.out
        var u = Uniforms(clock: SIMD4(Float(f.amount), Float(f.flow), f.still ? 1 : 0, 0),
                         layout: SIMD4(Float(view.drawableSize.width/view.bounds.width), Float(view.bounds.height/852), 0, 0))
        enc.setRenderPipelineState(pipeline); enc.setFragmentBytes(&u, length: MemoryLayout<Uniforms>.stride, index: 0)
        enc.setFragmentTexture(photo, index: 0); enc.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3); enc.endEncoding()
        let semaphore = inFlight; command.addCompletedHandler { _ in semaphore.signal() }
        command.present(drawable); command.commit(); onFrame?(f)
    }
}
private final class HoneyDrizzleStepperAdjustable: UIView {
    var adjust: ((Int) -> Void)?
    override func accessibilityIncrement() { adjust?(1) }
    override func accessibilityDecrement() { adjust?(-1) }
}
private final class HoneyDrizzleStepperView: UIView, UIGestureRecognizerDelegate {
    let metal: MTKView, renderer: HoneyDrizzleStepperRenderer
    var active = true
    private let chrome = HoneyDrizzleStepperChrome(), area = HoneyDrizzleStepperAdjustable()
    private let minus = UIButton(type: .custom), plus = UIButton(type: .custom)
    private let haptic = UIImpactFeedbackGenerator(style: .light)
    private var ticksSeen = 0, downY = 0.0, held = false
    override init(frame: CGRect) {
        let device = MTLCreateSystemDefaultDevice()!
        metal = MTKView(frame: .zero, device: device); renderer = HoneyDrizzleStepperRenderer(device)
        super.init(frame: frame)
        metal.colorPixelFormat = .rgba16Float
        (metal.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name: CGColorSpace.extendedSRGB)
        metal.preferredFramesPerSecond = 60; metal.delegate = renderer; metal.isUserInteractionEnabled = false
        metal.accessibilityElementsHidden = true
        addSubview(metal); addSubview(chrome); addSubview(area)
        chrome.isUserInteractionEnabled = false; chrome.accessibilityElementsHidden = true
        area.isAccessibilityElement = true; area.accessibilityTraits = .adjustable
        area.accessibilityLabel = "Honey"; area.accessibilityValue = "2 teaspoons"; area.accessibilityIdentifier = "honey-drizzle-stepper-honey"
        area.adjust = { [weak self] dir in self?.renderer.core.stepBy(dir); self?.refreshClock() }
        let drag = HoneyDrizzleStepperCatch(target: self, action: #selector(drag(_:)))
        drag.delegate = self; area.addGestureRecognizer(drag)
        for (button, label) in [(minus, "Less honey"), (plus, "More honey")] {
            button.accessibilityLabel = label; button.accessibilityIdentifier = button === minus ? "honey-drizzle-stepper-minus" : "honey-drizzle-stepper-plus"
            button.addTarget(self, action: #selector(buttonDown(_:)), for: .touchDown)
            button.addTarget(self, action: #selector(buttonUp), for: [.touchUpInside, .touchUpOutside, .touchCancel])
            addSubview(button)
        }
        accessibilityElements = [area, minus, plus]
        renderer.onFrame = { [weak self] f in
            guard let self else { return }; chrome.show(f)
            area.accessibilityValue = "\(f.number) \(f.number == 1 ? "teaspoon" : "teaspoons")"
            for _ in 0..<max(0, renderer.core.ticks-ticksSeen) { tick() }; ticksSeen = renderer.core.ticks
            if f.still && renderer.core.age < 0 { metal.isPaused = true }
        }
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    func refreshClock() { metal.isPaused = !active; if active { metal.draw() } }
    override func layoutSubviews() {
        super.layoutSubviews(); let sy = bounds.height/852
        metal.frame = bounds; chrome.frame = bounds; area.frame = CGRect(x: 0, y: 0, width: bounds.width, height: 556*sy)
        minus.frame = CGRect(x: 239, y: 776*sy, width: 61, height: 58); plus.frame = CGRect(x: 300, y: 776*sy, width: 61, height: 58)
        refreshClock()
    }
    @objc private func tick() { haptic.impactOccurred() }
    @objc private func buttonDown(_ sender: UIButton) { renderer.core.touchDown(); renderer.core.stepBy(sender === plus ? 1 : -1); refreshClock() }
    @objc private func buttonUp() { renderer.core.touchUp(); refreshClock() }
    @objc private func drag(_ g: HoneyDrizzleStepperCatch) {
        switch g.state {
        case .began: held = true; downY = g.point.y; renderer.core.touchDown(); renderer.core.dragStart()
        case .changed: renderer.core.dragTo(g.point.y-downY)
        case .ended, .cancelled, .failed: if held { renderer.core.dragEnd(); renderer.core.touchUp(); held = false }
        default: break
        }; refreshClock()
    }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
}
private final class HoneyDrizzleStepperCatch: UIGestureRecognizer {
    var point = CGPoint.zero
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) { guard let t = touches.first else { return }; point = t.location(in: view); state = .began }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) { guard let t = touches.first else { return }; point = t.location(in: view); state = .changed }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) { state = .ended }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) { state = .cancelled }
}
private final class HoneyDrizzleStepperChrome: UIView {
    private var frameState = HoneyDrizzleStepperMotion.Frame()
    private var backdrop: UIImage?
    private static func font(_ name: String, _ size: CGFloat) -> CTFont {
        let url = Bundle.main.url(forResource: "HoneyDrizzleStepper-"+name, withExtension: "ttf")!
        return CTFontCreateWithGraphicsFont(CGFont(CGDataProvider(url: url as CFURL)!)!, size, nil, nil)
    }
    private static let eyebrow = font("Inter-SemiBold", 12), headline = font("Fraunces-SemiBold", 56), number = font("Anton-Regular", 128)
    private static let hint = font("Inter-Medium", 14), unit = font("Inter-SemiBold", 20), sign = font("Inter-SemiBold", 28)
    override init(frame: CGRect) { super.init(frame: frame); isOpaque = false; backgroundColor = .clear }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    func show(_ f: HoneyDrizzleStepperMotion.Frame) { if frameState.number != f.number || frameState.pop != f.pop { frameState = f; setNeedsDisplay() } }
    private func color(_ hex: Int) -> UIColor { UIColor(red: CGFloat((hex>>16)&255)/255, green: CGFloat((hex>>8)&255)/255, blue: CGFloat(hex&255)/255, alpha: 1) }
    private func text(_ c: CGContext, _ s: String, _ font: CTFont, _ x: CGFloat, _ y: CGFloat, _ hex: Int, tracking: CGFloat = 0, center: Bool = false) {
        // CoreText shapes once with native kerning and fractional advances. Kern adds tracking after shaping.
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: s, attributes: [.font: font, .foregroundColor: color(hex)]))
        let width = CTLineGetTypographicBounds(line, nil, nil, nil)+Double(tracking)*Double(s.count)
        c.saveGState(); c.translateBy(x: x-(center ? width/2 : 0), y: y); c.scaleBy(x: 1, y: -1)
        c.setFillColor(color(hex).cgColor)
        var offset = 0
        for run in CTLineGetGlyphRuns(line) as! [CTRun] {
            let count = CTRunGetGlyphCount(run)
            var glyphs = [CGGlyph](repeating: 0, count: count), positions = [CGPoint](repeating: .zero, count: count)
            CTRunGetGlyphs(run, CFRange(location: 0, length: 0), &glyphs)
            CTRunGetPositions(run, CFRange(location: 0, length: 0), &positions)
            for i in positions.indices { positions[i].x += CGFloat(offset+i)*tracking }
            CTFontDrawGlyphs(font, &glyphs, &positions, count, c); offset += count
        }
        c.restoreGState()
    }
    override func layoutSubviews() {
        super.layoutSubviews(); guard bounds.width > 0 else { return }
        let format = UIGraphicsImageRendererFormat(); format.scale = window?.screen.scale ?? 3; format.opaque = false
        backdrop = UIGraphicsImageRenderer(size: bounds.size, format: format).image { ctx in
            let c = ctx.cgContext, sy = bounds.height/852, top = 776*sy
            text(c, "BUZZ · BREAKFAST", Self.eyebrow, 28, 318*sy, 0xFFE3C7, tracking: 4)
            text(c, "How", Self.headline, 26, 376*sy, 0xFFF6EA, tracking: -2)
            text(c, "sweet?", Self.headline, 26, 431*sy, 0xFFF6EA, tracking: -2)
            text(c, "Drag the dipper up for more.", Self.hint, 104, 760*sy, 0xFFD1A8)
            text(c, "tsp honey", Self.unit, 104, 786*sy, 0xFFE3C7)
            c.setFillColor(color(0xFFF6EA).cgColor); c.addPath(UIBezierPath(roundedRect: CGRect(x: 239, y: top, width: 122, height: 58), cornerRadius: 29).cgPath); c.fillPath()
            text(c, "−", Self.sign, 269, top+38, 0xC23A00, center: true); text(c, "+", Self.sign, 331, top+38, 0xC23A00, center: true)
            c.setFillColor(color(0xF2C9A8).cgColor); c.fill(CGRect(x: 299.5, y: top+16, width: 1, height: 26))
        }; setNeedsDisplay()
    }
    override func draw(_ rect: CGRect) {
        backdrop?.draw(at: .zero); guard let c = UIGraphicsGetCurrentContext() else { return }
        c.saveGState(); c.translateBy(x: 30, y: 804*bounds.height/852); c.scaleBy(x: frameState.pop, y: frameState.pop)
        text(c, String(frameState.number), Self.number, 0, 0, 0xFFF6EA); c.restoreGState()
    }
}
