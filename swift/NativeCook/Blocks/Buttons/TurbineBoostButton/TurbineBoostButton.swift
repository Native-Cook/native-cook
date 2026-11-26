import CoreText
import ImageIO
import MetalKit
import SwiftUI

struct TurbineBoostButton: View {
    static let loop = 5.0
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var phase
    var body: some View {
        TurbineBoostButtonSurface(autoPlay: autoPlay, reduced: reduced, active: phase == .active)
            .ignoresSafeArea().preferredColorScheme(.dark)
    }
}

private struct TurbineBoostButtonSurface: UIViewRepresentable {
    var autoPlay: Bool, reduced: Bool, active: Bool
    func makeUIView(context: Context) -> TurbineBoostButtonView { TurbineBoostButtonView() }
    func updateUIView(_ view: TurbineBoostButtonView, context: Context) {
        view.renderer.autoPlay = autoPlay; view.renderer.core.setReduced(reduced)
        view.metal.isPaused = !active
    }
}

private final class TurbineBoostButtonRenderer: NSObject, MTKViewDelegate {
    let core = TurbineBoostButtonMotion()
    var autoPlay = false
    var onFrame: ((TurbineBoostButtonMotion.Frame) -> Void)?
    var onReady: (() -> Void)?
    private var hasDrawn = false
    private let queue: MTLCommandQueue, pipeline: MTLRenderPipelineState, texture: MTLTexture
    private let inFlight = DispatchSemaphore(value: 3)
    struct Uniforms { var clock: SIMD4<Float>; var layout: SIMD4<Float> }
    init(device: MTLDevice) {
        queue = device.makeCommandQueue()!
        let descriptor = MTLRenderPipelineDescriptor(), library = device.makeDefaultLibrary()!
        descriptor.vertexFunction = library.makeFunction(name: "TurbineBoostButtonVertex")
        descriptor.fragmentFunction = library.makeFunction(name: "TurbineBoostButtonFragment")
        descriptor.colorAttachments[0].pixelFormat = .rgba16Float
        pipeline = try! device.makeRenderPipelineState(descriptor: descriptor)
        let url = Bundle.main.url(forResource: "TurbineBoostButton-fan", withExtension: "webp")!
        let source = CGImageSourceCreateWithURL(url as CFURL, nil)!
        let image = CGImageSourceCreateImageAtIndex(source, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary)!
        let w = image.width, h = image.height
        var pixels = [UInt8](repeating: 0, count: w*h*4)
        let cg = CGContext(data: &pixels, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w*4,
                           space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        cg.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))
        // Encoded sRGB bytes, deliberately NOT an _sRGB texture: the shader performs the reference's pow(2.2).
        let td = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .rgba8Unorm, width: w, height: h, mipmapped: false)
        texture = device.makeTexture(descriptor: td)!
        texture.replace(region: MTLRegionMake2D(0, 0, w, h), mipmapLevel: 0, withBytes: pixels, bytesPerRow: w*4)
        super.init()
    }
    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}
    func draw(in view: MTKView) {
        guard view.window != nil, view.bounds.width > 0, inFlight.wait(timeout: .now()) == .success else { return }
        guard let drawable = view.currentDrawable, let pass = view.currentRenderPassDescriptor,
              let command = queue.makeCommandBuffer(), let encoder = command.makeRenderCommandEncoder(descriptor: pass) else {
            inFlight.signal(); return
        }
        core.step(CACurrentMediaTime()*1000, autoPlay: autoPlay)
        let f = core.out
        var u = Uniforms(clock: SIMD4(Float(f.angle), Float(f.arc), Float(f.boost), 0),
                         layout: SIMD4(Float((view.bounds.width-393)/2), Float(252.1*(view.bounds.height/852-1)), Float(view.drawableSize.width/view.bounds.width), 0))
        encoder.setRenderPipelineState(pipeline)
        encoder.setFragmentBytes(&u, length: MemoryLayout<Uniforms>.stride, index: 0)
        encoder.setFragmentTexture(texture, index: 0)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3); encoder.endEncoding()
        let semaphore = inFlight, first = !hasDrawn; hasDrawn = true
        command.addCompletedHandler { [weak self] _ in
            semaphore.signal()
            if first { DispatchQueue.main.async { self?.onReady?() } }
        }
        command.present(drawable); command.commit(); onFrame?(f)
    }
}

private final class TurbineBoostButtonView: UIView, UIGestureRecognizerDelegate {
    let renderer: TurbineBoostButtonRenderer, metal: MTKView
    private let chrome = TurbineBoostButtonChrome(), pill = TurbineBoostButtonAccessible(), fan = UIView(), header = UIView()
    private var ticksSeen = 0, touching = false
    override init(frame: CGRect) {
        let device = MTLCreateSystemDefaultDevice()!
        renderer = TurbineBoostButtonRenderer(device: device); metal = MTKView(frame: .zero, device: device)
        super.init(frame: frame)
        backgroundColor = UIColor(red: 0, green: 2/255, blue: 17/255, alpha: 1)
        metal.colorPixelFormat = .rgba16Float
        (metal.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name: CGColorSpace.extendedSRGB)
        metal.preferredFramesPerSecond = 60; metal.delegate = renderer; metal.isUserInteractionEnabled = false
        metal.accessibilityElementsHidden = true; chrome.isUserInteractionEnabled = false; chrome.accessibilityElementsHidden = true
        // Reveal the copy after the first GPU frame: a cold install never shows labels over an empty fan.
        chrome.isHidden = true
        renderer.onReady = { [weak self] in self?.chrome.isHidden = false }
        [metal, chrome, fan, pill, header].forEach(addSubview)
        pill.isAccessibilityElement = true; pill.accessibilityTraits = .button
        pill.accessibilityLabel = "Boost, hold to spool up"; pill.accessibilityIdentifier = "turbine-boost-button-pill"
        pill.activate = { [weak self] in self?.renderer.core.activate() }
        fan.isAccessibilityElement = true; fan.accessibilityLabel = "Turbine fan, hold to spool up"
        fan.accessibilityIdentifier = "turbine-boost-button-fan"
        header.isAccessibilityElement = true; header.accessibilityTraits = .header
        header.accessibilityLabel = "Volt performance mode, boost"
        accessibilityElements = [header, pill, fan]
        let gesture = TurbineBoostButtonTouch(target: self, action: #selector(touch(_:)))
        gesture.delegate = self; addGestureRecognizer(gesture)
        renderer.onFrame = { [weak self] f in
            guard let self else { return }
            self.chrome.show(f)
            let value = "\(Int((100*f.share).rounded())) percent"
            self.pill.accessibilityValue = value; self.fan.accessibilityValue = value
            let count = self.renderer.core.ticks-self.ticksSeen; self.ticksSeen = self.renderer.core.ticks
            for _ in 0..<max(0, count) { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
            if self.renderer.core.full {
                self.renderer.core.full = false
                UINotificationFeedbackGenerator().notificationOccurred(.success)
                UIAccessibility.post(notification: .announcement, argument: "Full boost")
            }
        }
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func layoutSubviews() {
        super.layoutSubviews(); metal.frame = bounds; chrome.frame = bounds
        let ox = (bounds.width-393)/2, sy = bounds.height/852
        pill.frame = CGRect(x: ox+32, y: 793*sy-29, width: 329, height: 58)
        fan.frame = CGRect(x: ox+194.2-192, y: 252.1*sy+196.5-192, width: 384, height: 384)
        header.frame = CGRect(x: ox+28, y: 70*sy, width: 337, height: 145*sy)
    }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        let p = touch.location(in: self)
        return pill.frame.contains(p) || hypot(p.x-fan.frame.midX,p.y-fan.frame.midY) <= 192
    }
    @objc private func touch(_ gesture: TurbineBoostButtonTouch) {
        if gesture.state == .began {
            let p = gesture.point, c = CGPoint(x: fan.frame.midX, y: fan.frame.midY)
            if pill.frame.contains(p) || hypot(p.x-c.x,p.y-c.y) <= 192 {
                touching = true; renderer.core.hold()
            }
        } else if gesture.state == .ended || gesture.state == .cancelled || gesture.state == .failed {
            if touching { renderer.core.release(); touching = false }
        }
    }
}

private final class TurbineBoostButtonAccessible: UIView {
    var activate: (() -> Void)?
    override func accessibilityActivate() -> Bool { activate?(); return true }
}
/// Begins at contact, survives the gallery's simultaneous edge-back recognizer, and releases on cancellation.
private final class TurbineBoostButtonTouch: UIGestureRecognizer {
    var point = CGPoint.zero
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        guard let touch = touches.first, state == .possible else { return }
        point = touch.location(in: view); state = .began
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        if let touch = touches.first { point = touch.location(in: view) }; state = .changed
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) { state = .ended }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) { state = .cancelled }
}

private final class TurbineBoostButtonChromeModel: ObservableObject {
    @Published var frame = TurbineBoostButtonMotion.Frame()
}

private final class TurbineBoostButtonChrome: UIView {
    private let model = TurbineBoostButtonChromeModel()
    private let glyphs = TurbineBoostButtonTypography()
    private var host: UIHostingController<TurbineBoostButtonChromeCanvas>!
    override init(frame: CGRect) {
        super.init(frame: frame); isOpaque = false; backgroundColor = .clear
        host = UIHostingController(rootView: TurbineBoostButtonChromeCanvas(model: model, glyphs: glyphs))
        host.view.backgroundColor = .clear; host.view.isOpaque = false
        host.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]; addSubview(host.view)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func layoutSubviews() { super.layoutSubviews(); host.view.frame = bounds }
    func show(_ f: TurbineBoostButtonMotion.Frame) {
        let old = model.frame
        if old.boost != f.boost || old.held != f.held || (100*old.share).rounded() != (100*f.share).rounded() { model.frame = f }
    }
}

private final class TurbineBoostButtonTypography {
    private struct Line { let font: CTFont; let glyphs: [CGGlyph]; let positions: [CGPoint]; let width: CGFloat }
    private var lines = [String: Line]()
    private func line(_ text: String, _ name: String, _ size: CGFloat, _ tracking: CGFloat) -> Line {
        let key = "\(text)|\(name)|\(size)|\(tracking)"
        if let cached = lines[key] { return cached }
        let url = Bundle.main.url(forResource: "TurbineBoostButton-"+name, withExtension: "ttf")!
        let font = CTFontCreateWithGraphicsFont(CGFont(CGDataProvider(url: url as CFURL)!)!, size, nil, nil)
        let chars = Array(text.utf16); var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(font, chars, &glyphs, chars.count)
        var advances = [CGSize](repeating: .zero, count: chars.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &advances, chars.count)
        var x: CGFloat = 0; let positions = advances.map { advance -> CGPoint in defer { x += advance.width+tracking }; return CGPoint(x: x, y: 0) }
        var result = Line(font: font, glyphs: glyphs, positions: positions, width: x)
        // Match RN's final paragraph shaping: retain natural font kerning, then add tracking per glyph.
        // The percent deliberately retains raw digit advances, as RN's separate drawPercent does.
        if name != "InterDisplay-Bold" {
            let shaped = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: [
                NSAttributedString.Key(kCTFontAttributeName as String): font,
                NSAttributedString.Key(kCTLigatureAttributeName as String): 0,
            ]))
            var shapedGlyphs = [CGGlyph](), shapedPositions = [CGPoint]()
            for case let run as CTRun in CTLineGetGlyphRuns(shaped) as NSArray {
                let count = CTRunGetGlyphCount(run)
                var ids = [CGGlyph](repeating: 0,count: count), points = [CGPoint](repeating: .zero,count: count)
                CTRunGetGlyphs(run,CFRange(location: 0,length: 0),&ids)
                CTRunGetPositions(run,CFRange(location: 0,length: 0),&points)
                shapedGlyphs += ids; shapedPositions += points
            }
            if shapedGlyphs.count == glyphs.count, let first = shapedPositions.first {
                for i in shapedPositions.indices { shapedPositions[i].x += CGFloat(i)*tracking-first.x }
                result = Line(font: font,glyphs: shapedGlyphs,positions: shapedPositions,
                              width: CGFloat(CTLineGetTypographicBounds(shaped,nil,nil,nil))+CGFloat(glyphs.count)*tracking)
            }
        }
        lines[key] = result; return result
    }
    func drawLine(_ text: String, _ font: String, _ size: CGFloat, _ tracking: CGFloat, _ cx: CGFloat, _ y: CGFloat, _ color: UInt32, _ cg: CGContext) {
        let l = line(text, font, size, tracking)
        cg.saveGState(); cg.translateBy(x: cx-l.width/2, y: y); cg.scaleBy(x: 1, y: -1)
        cg.setFillColor(Self.color(color)); CTFontDrawGlyphs(l.font, l.glyphs, l.positions, l.glyphs.count, cg); cg.restoreGState()
    }
    static func color(_ c: UInt32, _ alpha: CGFloat = 1) -> CGColor { UIColor(red: CGFloat(c>>16&255)/255, green: CGFloat(c>>8&255)/255, blue: CGFloat(c&255)/255, alpha: alpha).cgColor }
}

private struct TurbineBoostButtonChromeCanvas: View {
    @ObservedObject var model: TurbineBoostButtonChromeModel
    let glyphs: TurbineBoostButtonTypography
    var body: some View {
        Canvas(opaque: false, colorMode: .nonLinear) { context, size in
            let f = model.frame, b = CGFloat(f.boost), cx = size.width/2, sy = size.height/852
            context.withCGContext { cg in glyphs.drawLine("VOLT · PERFORMANCE MODE", "Inter-SemiBold", 12, 4, cx, 92*sy, 0x6F9BFF, cg) }
            // SwiftUI's blur radius is the Gaussian standard deviation, unlike CG's legacy shadow blur.
            if b > 0.002 {
                context.drawLayer { glow in
                    glow.opacity = 0.9*b; glow.addFilter(.blur(radius: 15*b))
                    glow.withCGContext { cg in glyphs.drawLine("BOOST", "ArchivoExpanded-Black", 66, -4, cx, 166*sy, 0x3D7BFF, cg) }
                }
            }
            context.withCGContext { cg in
                glyphs.drawLine("BOOST", "ArchivoExpanded-Black", 66, -4, cx, 166*sy, 0xFFFFFF, cg)
                glyphs.drawLine(b > 0.98 ? "Full boost. Let go to coast." : "Hold to spool up. Let go to coast.", "Inter-Medium", 15, 0, cx, 204*sy, 0x8EA6D6, cg)
                glyphs.drawLine("\(Int((100*f.share).rounded()))%", "InterDisplay-Bold", 40, -1, cx, 726*sy, 0xCFE0FF, cg)
            }
            var pill = context; pill.translateBy(x: cx,y: 793*sy)
            if f.held { pill.scaleBy(x: 0.97,y: 0.97) }
            pill.withCGContext { cg in
                let path = CGPath(roundedRect: CGRect(x: -164.5,y: -29,width: 329,height: 58),cornerWidth: 29,cornerHeight: 29,transform: nil)
                cg.addPath(path); cg.setFillColor(TurbineBoostButtonTypography.color(f.held ? 0x1A3BA8 : 0x0E1A36)); cg.fillPath()
                cg.addPath(path); cg.setStrokeColor(TurbineBoostButtonTypography.color(f.held ? 0x5C8DFF : 0x2B4A8F)); cg.setLineWidth(1); cg.strokePath()
            }
            pill.drawLayer { glow in
                glow.addFilter(.blur(radius: 6+10*b))
                glow.fill(Path(ellipseIn: CGRect(x: -63,y: -5,width: 10,height: 10)),with: .color(Color(red: 61/255,green: 123/255,blue: 1)))
            }
            pill.withCGContext { cg in
                cg.setFillColor(TurbineBoostButtonTypography.color(0x3D7BFF)); cg.fillEllipse(in: CGRect(x: -63,y: -5,width: 10,height: 10))
                glyphs.drawLine(f.held ? "Boosting…" : "Hold to boost", "Inter-SemiBold", 17, 0, 8, 6, 0xE4EDFF, cg)
            }
        }.ignoresSafeArea()
    }
}
