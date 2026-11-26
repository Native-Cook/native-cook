import SwiftUI
import MetalKit
import CoreText
import ImageIO

struct PaperPleatProgress: View {
    var autoPlay = false
    static let loop = 7.0
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scene
    var body: some View {
        PaperPleatProgressSurface(autoPlay: autoPlay, reduced: reduced, active: scene == .active)
            .ignoresSafeArea().background(Color(red: 229/255, green: 217/255, blue: 207/255))
    }
}
private struct PaperPleatProgressSurface: UIViewRepresentable {
    var autoPlay: Bool, reduced: Bool, active: Bool
    func makeUIView(context: Context) -> PaperPleatProgressView { PaperPleatProgressView() }
    func updateUIView(_ v: PaperPleatProgressView, context: Context) {
        v.renderer.autoPlay = autoPlay; v.renderer.core.setStill(reduced); v.active = active; v.wake()
    }
}

/// Explicit sRGB decoding; each byte mip is a rounded 2×2 box, made once, never GPU-generated.
private struct PaperPleatProgressPixels {
    var bytes: [UInt8], width: Int, height: Int
    init(_ name: String) {
        let url = Bundle.main.url(forResource: "PaperPleatProgress-"+name, withExtension: "webp")!
        let source = CGImageSourceCreateWithURL(url as CFURL, nil)!
        let image = CGImageSourceCreateImageAtIndex(source, 0, nil)!
        width = image.width; height = image.height; bytes = [UInt8](repeating: 0, count: width*height*4)
        let c = CGContext(data: &bytes, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width*4,
                          space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        c.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
    }
    private init(_ bytes: [UInt8], _ width: Int, _ height: Int) { self.bytes = bytes; self.width = width; self.height = height }
    func half() -> Self {
        let w = width/2, h = height/2; var result = [UInt8](repeating: 0, count: w*h*4)
        for y in 0..<h { for x in 0..<w { for k in 0..<4 {
            let i = (y*2*width+x*2)*4+k
            result[(y*w+x)*4+k] = UInt8((Int(bytes[i])+Int(bytes[i+4])+Int(bytes[i+width*4])+Int(bytes[i+width*4+4])+2)/4)
        } } }; return Self(result, w, h)
    }
    func level(_ n: Int) -> Self { var out = self; for _ in 0..<n { out = out.half() }; return out }
    func texture(_ device: MTLDevice) -> MTLTexture {
        let t = device.makeTexture(descriptor: .texture2DDescriptor(pixelFormat: .rgba8Unorm, width: width, height: height, mipmapped: false))!
        t.replace(region: MTLRegionMake2D(0, 0, width, height), mipmapLevel: 0, withBytes: bytes, bytesPerRow: width*4); return t
    }
}
private final class PaperPleatProgressRenderer: NSObject, MTKViewDelegate {
    let core = PaperPleatProgressMotion()
    var autoPlay = false
    var onFrame: ((PaperPleatProgressMotion.Frame) -> Void)?
    let queue: MTLCommandQueue, pipeline: MTLRenderPipelineState, textures: [MTLTexture]
    private let inFlight = DispatchSemaphore(value: 3)
    struct Uniforms {
        var p: Float, sw: Float, t: Float, boost: Float, fade: Float, lift: Float
        var origin: SIMD2<Float>; var density: Float
    }
    init(_ device: MTLDevice) {
        queue = device.makeCommandQueue()!
        let library = device.makeDefaultLibrary()!, d = MTLRenderPipelineDescriptor()
        d.vertexFunction = library.makeFunction(name: "PaperPleatProgressVertex")
        d.fragmentFunction = library.makeFunction(name: "PaperPleatProgressFragment")
        d.colorAttachments[0].pixelFormat = .rgba16Float; pipeline = try! device.makeRenderPipelineState(descriptor: d)
        let photo = PaperPleatProgressPixels("pleat"), plate = PaperPleatProgressPixels("pleat-plate"), mask = PaperPleatProgressPixels("pleat-mask")
        let plate5 = plate.level(5)
        textures = [photo, plate, mask, photo.level(4), plate5, plate5.half(), mask.level(5)].map { $0.texture(device) }
        super.init()
    }
    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}
    func draw(in view: MTKView) {
        guard view.window != nil, view.bounds.width > 0, inFlight.wait(timeout: .now()) == .success else { return }
        guard let drawable = view.currentDrawable, let pass = view.currentRenderPassDescriptor,
              let command = queue.makeCommandBuffer(), let enc = command.makeRenderCommandEncoder(descriptor: pass) else { inFlight.signal(); return }
        core.step(CACurrentMediaTime(), autoPlay: autoPlay); let f = core.out
        var u = Uniforms(p: Float(f.p), sw: Float(f.sw), t: Float(f.t), boost: Float(f.boost), fade: Float(f.fade), lift: Float(f.lift),
                         origin: SIMD2(0, Float(150*view.bounds.height/852)), density: Float(view.drawableSize.width/view.bounds.width))
        enc.setRenderPipelineState(pipeline); enc.setFragmentBytes(&u, length: MemoryLayout<Uniforms>.stride, index: 0)
        for (i, t) in textures.enumerated() { enc.setFragmentTexture(t, index: i) }
        enc.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3); enc.endEncoding()
        let semaphore = inFlight; command.addCompletedHandler { _ in semaphore.signal() }
        command.present(drawable); command.commit(); onFrame?(f)
    }
}
private final class PaperPleatProgressView: UIView, UIGestureRecognizerDelegate {
    let metal: MTKView, renderer: PaperPleatProgressRenderer
    var active = true
    private let chrome = PaperPleatProgressChrome(), progress = UIProgressView(progressViewStyle: .default), sculpture = UIView()
    private let haptic = UIImpactFeedbackGenerator(style: .light)
    private var donesSeen = 0, held = false
    override init(frame: CGRect) {
        let device = MTLCreateSystemDefaultDevice()!
        metal = MTKView(frame: .zero, device: device); renderer = PaperPleatProgressRenderer(device)
        super.init(frame: frame)
        metal.colorPixelFormat = .rgba16Float
        (metal.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name: CGColorSpace.extendedSRGB)
        metal.preferredFramesPerSecond = 60; metal.delegate = renderer; metal.isUserInteractionEnabled = false
        metal.accessibilityElementsHidden = true; chrome.isUserInteractionEnabled = false; chrome.accessibilityElementsHidden = true
        addSubview(metal); addSubview(chrome); addSubview(sculpture); addSubview(progress)
        sculpture.isAccessibilityElement = true; sculpture.accessibilityLabel = "Paper sculpture"; sculpture.accessibilityIdentifier = "paper-pleat-progress-sculpture"
        progress.trackTintColor = .clear; progress.progressTintColor = .clear
        progress.isAccessibilityElement = true; progress.accessibilityIdentifier = "paper-pleat-progress-export"
        accessibilityElements = [progress, sculpture]
        let touch = PaperPleatProgressTouch(target: self, action: #selector(touch(_:))); touch.delegate = self; addGestureRecognizer(touch)
        renderer.onFrame = { [weak self] f in
            guard let self else { return }; chrome.show(f)
            let pct = Int((100*f.pct).rounded()); progress.progress = Float(f.pct)
            progress.accessibilityLabel = pct >= 100 ? "Exported" : "Exporting, \(pct) percent"; progress.accessibilityValue = "\(pct)%"
            for _ in 0..<max(0, renderer.core.dones-donesSeen) { completed() }; donesSeen = renderer.core.dones
            if renderer.core.settled { metal.isPaused = true }
        }
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    func wake() { metal.isPaused = !active; if active { metal.draw() } }
    override func layoutSubviews() {
        super.layoutSubviews(); let sy = bounds.height/852
        metal.frame = bounds; chrome.frame = bounds; sculpture.frame = CGRect(x: 0, y: 150*sy, width: 393, height: 589.5)
        progress.frame = CGRect(x: 24, y: 690*sy, width: 345, height: 130*sy); wake()
    }
    @objc private func completed() { haptic.impactOccurred(); UIAccessibility.post(notification: .announcement, argument: "Exported") }
    @objc private func touch(_ g: PaperPleatProgressTouch) {
        switch g.state {
        case .began: held = true; renderer.core.touchDown(sculpture.frame.contains(g.point))
        case .ended, .cancelled, .failed: if held { renderer.core.touchUp(); held = false }
        default: break
        }; wake()
    }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
}
private final class PaperPleatProgressTouch: UIGestureRecognizer {
    var point = CGPoint.zero
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) { guard let t = touches.first else { return }; point = t.location(in: view); state = .began }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) { state = .changed }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) { state = .ended }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) { state = .cancelled }
}

/// Shaping and fractional glyph advances are cached for every percent and remaining-time value.
private final class PaperPleatProgressChrome: UIView {
    struct Line { var font: CTFont, glyphs: [CGGlyph], positions: [CGPoint] }
    private var f = PaperPleatProgressMotion.Frame()
    private static func font(_ name: String, _ size: CGFloat) -> CTFont {
        let url = Bundle.main.url(forResource: "PaperPleatProgress-"+name, withExtension: "ttf")!
        return CTFontCreateWithGraphicsFont(CGFont(CGDataProvider(url: url as CFURL)!)!, size, nil, nil)
    }
    private static func line(_ text: String, _ font: CTFont, _ tracking: CGFloat = 0) -> Line {
        let shaped = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: [.font: font]))
        var glyphs = [CGGlyph](), positions = [CGPoint]()
        for run in CTLineGetGlyphRuns(shaped) as! [CTRun] {
            let n = CTRunGetGlyphCount(run); var gs = [CGGlyph](repeating: 0, count: n), ps = [CGPoint](repeating: .zero, count: n)
            CTRunGetGlyphs(run, CFRange(location: 0, length: 0), &gs); CTRunGetPositions(run, CFRange(location: 0, length: 0), &ps)
            for i in ps.indices { ps[i].x += CGFloat(glyphs.count+i)*tracking }; glyphs += gs; positions += ps
        }; return Line(font: font, glyphs: glyphs, positions: positions)
    }
    private static let title = font("InstrumentSerif-Regular", 60), percent = font("InterDisplay-Bold", 64), meta = font("Inter-Medium", 15)
    private static let folding = line("Folding your", title, -1.5), exportLine = line("export", title, -1.5), exported = line("Exported.", title, -1.5)
    private static let percents = (0...100).map { line("\($0)%", percent, -2) }
    private static let left = (0...25).map { line("Annual report.pdf · 18 MB · \($0) s left", meta) }
    private static let saved = line("Annual report.pdf · 18 MB · saved to Files", meta)
    override init(frame: CGRect) { super.init(frame: frame); isOpaque = false; backgroundColor = .clear; _ = Self.percents; _ = Self.left }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    func show(_ frame: PaperPleatProgressMotion.Frame) { f = frame; setNeedsDisplay() }
    private func color(_ hex: Int, _ alpha: CGFloat = 1) -> CGColor {
        CGColor(colorSpace: CGColorSpace(name: CGColorSpace.sRGB)!, components: [CGFloat((hex>>16)&255)/255, CGFloat((hex>>8)&255)/255, CGFloat(hex&255)/255, alpha])!
    }
    private func draw(_ line: Line, _ y: CGFloat, _ hex: Int, _ c: CGContext) {
        c.saveGState(); c.translateBy(x: 30, y: y); c.scaleBy(x: 1, y: -1); c.setFillColor(color(hex))
        CTFontDrawGlyphs(line.font, line.glyphs, line.positions, line.glyphs.count, c); c.restoreGState()
    }
    override func draw(_ rect: CGRect) {
        guard let c = UIGraphicsGetCurrentContext() else { return }; let sy = bounds.height/852
        draw(f.done ? Self.exported : Self.folding, 140*sy, 0x2B2420, c)
        if !f.done { draw(Self.exportLine, 199*sy, 0x2B2420, c) }
        draw(Self.percents[min(100, max(0, Int((100*f.pct).rounded())))], 754*sy, 0xE0613F, c)
        draw(f.done ? Self.saved : Self.left[min(25, max(0, Int(ceil(25*(1-f.pct)))))], 786*sy, 0x7B6E64, c)
        c.setFillColor(color(0x2B2420, 0.12)); c.addPath(UIBezierPath(roundedRect: CGRect(x: 30, y: 806*sy, width: 333, height: 4), cornerRadius: 2).cgPath); c.fillPath()
        if f.pct > 0 { c.setFillColor(color(0xE0613F)); c.addPath(UIBezierPath(roundedRect: CGRect(x: 30, y: 806*sy, width: 333*f.pct, height: 4), cornerRadius: 2).cgPath); c.fillPath() }
    }
}
