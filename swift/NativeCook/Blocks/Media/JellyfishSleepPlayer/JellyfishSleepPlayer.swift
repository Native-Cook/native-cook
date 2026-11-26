import SwiftUI
import MetalKit
import CoreText
import ImageIO

struct JellyfishSleepPlayer: View {
    var autoPlay = false
    static let loop = 8.0
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scene
    var body: some View {
        JellyfishSleepPlayerSurface(autoPlay: autoPlay, reduced: reduced, active: scene == .active)
            .preferredColorScheme(.dark).ignoresSafeArea().background(Color(red: 2/255, green: 12/255, blue: 28/255))
    }
}
private struct JellyfishSleepPlayerSurface: UIViewRepresentable {
    var autoPlay: Bool, reduced: Bool, active: Bool
    func makeUIView(context: Context) -> JellyfishSleepPlayerView { JellyfishSleepPlayerView() }
    func updateUIView(_ v: JellyfishSleepPlayerView, context: Context) {
        v.renderer.autoPlay = autoPlay; v.renderer.core.setStill(reduced); v.metal.isPaused = !active
    }
}

private final class JellyfishSleepPlayerRenderer: NSObject, MTKViewDelegate {
    let core = JellyfishSleepPlayerMotion()
    var autoPlay = false
    var onFrame: ((JellyfishSleepPlayerMotion.Frame) -> Void)?
    let queue: MTLCommandQueue, pipeline: MTLRenderPipelineState, photo: MTLTexture
    private let inFlight = DispatchSemaphore(value: 3)
    struct Uniforms { var clock: SIMD4<Float>, tracks: SIMD4<Float>, layout: SIMD4<Float>, viewport: SIMD4<Float> }
    init(_ device: MTLDevice) {
        queue = device.makeCommandQueue()!
        let library = device.makeDefaultLibrary()!, d = MTLRenderPipelineDescriptor()
        d.vertexFunction = library.makeFunction(name: "JellyfishSleepPlayerVertex")
        d.fragmentFunction = library.makeFunction(name: "JellyfishSleepPlayerFragment")
        d.colorAttachments[0].pixelFormat = .rgba16Float; pipeline = try! device.makeRenderPipelineState(descriptor: d)
        // Decode eagerly into sRGB bytes; sample as unorm to preserve their gamma-encoded values.
        let url = Bundle.main.url(forResource: "JellyfishSleepPlayer-jellyfish", withExtension: "webp")!
        let source = CGImageSourceCreateWithURL(url as CFURL, nil)!, image = CGImageSourceCreateImageAtIndex(source,0,nil)!
        let w = image.width, h = image.height
        var bytes = [UInt8](repeating: 0, count: w*h*4)
        let c = CGContext(data: &bytes, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w*4,
                          space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        c.draw(image,in: CGRect(x: 0,y: 0,width: w,height: h))
        photo = device.makeTexture(descriptor: .texture2DDescriptor(pixelFormat: .rgba8Unorm,width: w,height: h,mipmapped: false))!
        photo.replace(region: MTLRegionMake2D(0,0,w,h),mipmapLevel: 0,withBytes: bytes,bytesPerRow: w*4)
        super.init()
    }
    static func uniforms(_ f: JellyfishSleepPlayerMotion.Frame, _ size: CGSize, _ density: CGFloat) -> Uniforms {
        let ox = (size.width-393)/2
        return Uniforms(clock: SIMD4(Float(f.t),Float(f.m),Float(f.c),Float(f.wave)),
                        tracks: SIMD4(Float(f.a),Float(f.b),Float(f.k),Float(f.lit)),
                        layout: SIMD4(Float(ox-0.41),Float(34*size.height/852),Float(ox),0),
                        viewport: SIMD4(Float(density),0,0,0))
    }
    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}
    func draw(in view: MTKView) {
        guard view.window != nil, view.bounds.width > 0, inFlight.wait(timeout: .now()) == .success else { return }
        guard let drawable = view.currentDrawable, let pass = view.currentRenderPassDescriptor,
              let command = queue.makeCommandBuffer(), let enc = command.makeRenderCommandEncoder(descriptor: pass) else { inFlight.signal(); return }
        core.step(CACurrentMediaTime(),autoPlay: autoPlay); let f = core.out
        var u = Self.uniforms(f,view.bounds.size,view.drawableSize.width/view.bounds.width)
        enc.setRenderPipelineState(pipeline); enc.setFragmentBytes(&u,length: MemoryLayout<Uniforms>.stride,index: 0)
        enc.setFragmentTexture(photo,index: 0); enc.drawPrimitives(type: .triangle,vertexStart: 0,vertexCount: 3); enc.endEncoding()
        let semaphore = inFlight; command.addCompletedHandler { _ in semaphore.signal() }
        command.present(drawable); command.commit(); onFrame?(f)
    }
}

private final class JellyfishSleepPlayerView: UIView, UIGestureRecognizerDelegate {
    let metal: MTKView, renderer: JellyfishSleepPlayerRenderer
    private let chrome = JellyfishSleepPlayerChrome(), heading = UIView(), swipe = UIView()
    private let previous = UIButton(type: .custom), play = UIButton(type: .custom), nextButton = UIButton(type: .custom)
    private let haptic = UIImpactFeedbackGenerator(style: .light)
    private var held = false, origin = CGPoint.zero, lastPoint = CGPoint.zero, lastTime = 0.0, speed = 0.0
    override init(frame: CGRect) {
        let device = MTLCreateSystemDefaultDevice()!
        metal = MTKView(frame: .zero,device: device); renderer = JellyfishSleepPlayerRenderer(device)
        super.init(frame: frame)
        metal.colorPixelFormat = .rgba16Float
        (metal.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name: CGColorSpace.extendedSRGB)
        metal.preferredFramesPerSecond = 60; metal.delegate = renderer; metal.isUserInteractionEnabled = false
        metal.accessibilityElementsHidden = true; chrome.accessibilityElementsHidden = true; chrome.isUserInteractionEnabled = false
        addSubview(metal); addSubview(chrome); addSubview(swipe); addSubview(heading)
        heading.isAccessibilityElement = true; heading.accessibilityTraits = .header
        heading.accessibilityIdentifier = "jellyfish-sleep-player-title"
        swipe.accessibilityIdentifier = "jellyfish-sleep-player-swipe"
        let drag = JellyfishSleepPlayerTouch(target: self,action: #selector(drag(_:))); drag.delegate = self; swipe.addGestureRecognizer(drag)
        for (button,label,id) in [(previous,"Previous track","previous"),(play,"Pause","play"),(nextButton,"Next track","next")] {
            button.accessibilityLabel = label; button.accessibilityIdentifier = "jellyfish-sleep-player-"+id
            button.addTarget(self,action: #selector(down),for: .touchDown)
            button.addTarget(self,action: #selector(up),for: [.touchUpInside,.touchUpOutside,.touchCancel])
            button.addTarget(self,action: #selector(activate(_:)),for: .touchUpInside); addSubview(button)
        }
        accessibilityElements = [heading,previous,play,nextButton]
        renderer.onFrame = { [weak self] f in
            guard let self else { return }; chrome.show(f)
            let i = f.k > 0.5 ? f.b : f.a
            heading.accessibilityLabel = JellyfishSleepPlayerChrome.names[i]; heading.accessibilityHint = JellyfishSleepPlayerChrome.metas[i]
            play.accessibilityLabel = f.playing ? "Pause" : "Play"
        }
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func layoutSubviews() {
        super.layoutSubviews(); let ox = (bounds.width-393)/2, sy = bounds.height/852, top = 776*sy
        metal.frame = bounds; chrome.frame = bounds
        swipe.frame = CGRect(x: 0,y: 190*sy,width: bounds.width,height: 480*sy)
        heading.frame = CGRect(x: ox+32,y: 676*sy,width: 300,height: 56*sy)
        previous.frame = CGRect(x: ox+76,y: top+4,width: 48,height: 48)
        play.frame = CGRect(x: ox+156.5,y: top,width: 80,height: 56)
        nextButton.frame = CGRect(x: ox+269,y: top+4,width: 48,height: 48)
    }
    @objc private func down() { renderer.core.touchDown() }
    @objc private func up() { renderer.core.touchUp() }
    private func change(_ dir: Int) {
        let target = renderer.core.changeTrack(dir)
        haptic.impactOccurred(); UIAccessibility.post(notification: .announcement,argument: JellyfishSleepPlayerChrome.names[target])
    }
    @objc private func activate(_ sender: UIButton) {
        if sender === play { renderer.core.togglePlay() } else { change(sender === nextButton ? 1 : -1) }
    }
    @objc private func drag(_ g: JellyfishSleepPlayerTouch) {
        switch g.state {
        case .began:
            held = true; origin = g.point; lastPoint = g.point; lastTime = g.time; speed = 0; down()
        case .changed:
            if g.time > lastTime { speed = (g.point.x-lastPoint.x)/(g.time-lastTime) }
            lastPoint = g.point; lastTime = g.time
        case .ended,.cancelled,.failed:
            if held {
                let dx = g.point.x-origin.x, dy = g.point.y-origin.y, velocity = g.time-lastTime < 0.1 ? speed : 0
                if g.state == .ended && abs(dx) >= abs(dy) && (abs(dx) >= 40 || abs(velocity) > 500) {
                    change((abs(dx) >= 40 ? dx : velocity) < 0 ? 1 : -1)
                }
                up(); held = false
            }
        default: break
        }
    }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
}
private final class JellyfishSleepPlayerTouch: UIGestureRecognizer {
    var point = CGPoint.zero, time = 0.0
    private func read(_ touches: Set<UITouch>) {
        guard let touch = touches.first else { return }; point = touch.location(in: view); time = touch.timestamp
    }
    override func touchesBegan(_ touches: Set<UITouch>,with event: UIEvent) { read(touches); state = .began }
    override func touchesMoved(_ touches: Set<UITouch>,with event: UIEvent) { read(touches); state = .changed }
    override func touchesEnded(_ touches: Set<UITouch>,with event: UIEvent) { read(touches); state = .ended }
    override func touchesCancelled(_ touches: Set<UITouch>,with event: UIEvent) { read(touches); state = .cancelled }
}

/// Cache shaped glyph positions once, retaining native kerning before adding per-glyph tracking.
private final class JellyfishSleepPlayerChrome: UIView {
    static let names = ["Moon Jelly","Glass Tide","Amber Drift"]
    static let metas = ["Ocean hum · 42 min · rain under it","Slow surf · 38 min · far gulls","Warm drone · 45 min · low wind"]
    static let colours = [0xFF8AD8,0x6FE6FF,0xFFB45A]
    struct Line { var font: CTFont, glyphs: [CGGlyph], positions: [CGPoint], width: CGFloat }
    private static func font(_ name: String,_ size: CGFloat) -> CTFont {
        let url = Bundle.main.url(forResource: "JellyfishSleepPlayer-"+name,withExtension: "ttf")!
        return CTFontCreateWithGraphicsFont(CGFont(CGDataProvider(url: url as CFURL)!)!,size,nil,nil)
    }
    private static func line(_ text: String,_ font: CTFont,_ tracking: CGFloat = 0) -> Line {
        let shaped = CTLineCreateWithAttributedString(NSAttributedString(string: text,attributes: [.font: font]))
        var glyphs = [CGGlyph](), positions = [CGPoint]()
        for run in CTLineGetGlyphRuns(shaped) as! [CTRun] {
            let n = CTRunGetGlyphCount(run); var gs = [CGGlyph](repeating: 0,count: n), ps = [CGPoint](repeating: .zero,count: n)
            CTRunGetGlyphs(run,CFRange(location: 0,length: 0),&gs); CTRunGetPositions(run,CFRange(location: 0,length: 0),&ps)
            for i in ps.indices { ps[i].x += CGFloat(glyphs.count+i)*tracking }; glyphs += gs; positions += ps
        }
        return Line(font: font,glyphs: glyphs,positions: positions,width: CTLineGetTypographicBounds(shaped,nil,nil,nil)+CGFloat(glyphs.count)*tracking)
    }
    private static let eyebrow = line("DRIFT · SLEEP SOUNDS",font("Inter-SemiBold",12),4)
    private static let headline = line("Drift off.",font("InstrumentSerif-Regular",86),-2)
    private static let titles = names.map { line($0,font("InterDisplay-Bold",26),-0.5) }
    private static let metadata = metas.map { line($0,font("Inter-Medium",15)) }
    private static let start = line("16:02",font("Inter-Medium",12)), end = line("42:00",font("Inter-Medium",12))
    private var f = JellyfishSleepPlayerMotion.Frame()
    override init(frame: CGRect) { super.init(frame: frame); isOpaque = false; backgroundColor = .clear; _ = Self.headline }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    func show(_ frame: JellyfishSleepPlayerMotion.Frame) { f = frame; setNeedsDisplay() }
    private func colour(_ hex: Int,_ alpha: CGFloat = 1) -> CGColor {
        CGColor(colorSpace: CGColorSpace(name: CGColorSpace.sRGB)!,components: [CGFloat(hex>>16&255)/255,CGFloat(hex>>8&255)/255,CGFloat(hex&255)/255,alpha])!
    }
    private func text(_ line: Line,_ x: CGFloat,_ y: CGFloat,_ hex: Int,_ alpha: Double,_ c: CGContext) {
        c.saveGState(); c.translateBy(x: x,y: y); c.scaleBy(x: 1,y: -1); c.setFillColor(colour(hex,alpha))
        CTFontDrawGlyphs(line.font,line.glyphs,line.positions,line.glyphs.count,c); c.restoreGState()
    }
    override func draw(_ rect: CGRect) {
        guard let c = UIGraphicsGetCurrentContext() else { return }
        let ox = (bounds.width-393)/2, sy = bounds.height/852, cx = bounds.width/2
        let scrim = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB),colors: [colour(0x020C1C,0),colour(0x020C1C,0.88),colour(0x020C1C)] as CFArray,locations: [0,0.45,1])!
        c.drawLinearGradient(scrim,start: CGPoint(x: 0,y: 540*sy),end: CGPoint(x: 0,y: bounds.height),options: [])
        text(Self.eyebrow,cx-Self.eyebrow.width/2,90*sy,Self.colours[f.a],1-f.k,c)
        if f.k > 0 { text(Self.eyebrow,cx-Self.eyebrow.width/2,90*sy,Self.colours[f.b],f.k,c) }
        text(Self.headline,cx-Self.headline.width/2,170*sy,0xFFF4FB,1,c)
        func track(_ i: Int,_ dx: Double,_ alpha: Double) {
            text(Self.titles[i],ox+32+dx,700*sy,0xFFFFFF,alpha,c)
            text(Self.metadata[i],ox+32+dx,727*sy,0xD7C6FF,alpha,c)
        }
        if f.k > 0 {
            let k1 = min(1,f.k*2), k2 = max(0,f.k*2-1)
            if k1 < 1 { track(f.a,-30*k1,1-k1) }; if k2 > 0 { track(f.b,30*(1-k2),k2) }
        } else { track(f.a,0,1) }
        let cc = Self.colours[f.k > 0.5 ? f.b : f.a]
        func pill(_ r: CGRect,_ radius: CGFloat,_ hex: Int,_ alpha: CGFloat = 1) {
            c.setFillColor(colour(hex,alpha)); c.addPath(UIBezierPath(roundedRect: r,cornerRadius: radius).cgPath); c.fillPath()
        }
        pill(CGRect(x: ox+32,y: 748*sy,width: 329,height: 3),1.5,0xFFFFFF,0.18)
        let fill = CGRect(x: ox+32,y: 748*sy,width: 329*max(0.02,f.prog),height: 3)
        c.saveGState(); c.setShadow(offset: .zero,blur: 10,color: colour(cc)); pill(fill,1.5,cc); c.restoreGState()
        text(Self.start,ox+32,770*sy,0xB7A8E0,1,c); text(Self.end,ox+361-Self.end.width,770*sy,0xB7A8E0,1,c)
        let top = 776*sy
        c.saveGState(); c.setShadow(offset: .zero,blur: 34,color: colour(cc)); pill(CGRect(x: ox+156.5,y: top,width: 80,height: 56),28,0xFFF4FB); c.restoreGState()
        c.setFillColor(colour(0x2A0F3D))
        if f.playing {
            for x in [187.0,197] { pill(CGRect(x: ox+x,y: top+18,width: 6,height: 20),2,0x2A0F3D) }
        } else {
            c.move(to: CGPoint(x: ox+187.5,y: top+17)); c.addLine(to: CGPoint(x: ox+205.5,y: top+28)); c.addLine(to: CGPoint(x: ox+187.5,y: top+39)); c.closePath()
            c.setStrokeColor(colour(0x2A0F3D)); c.setLineWidth(3); c.setLineJoin(.round); c.drawPath(using: .fillStroke)
        }
        c.setFillColor(colour(0xFFFFFF,0.9))
        for (base,tip,bar) in [(108.0,94.0,89.0),(285,299,301.5)] {
            c.move(to: CGPoint(x: ox+base,y: top+16)); c.addLine(to: CGPoint(x: ox+base,y: top+42)); c.addLine(to: CGPoint(x: ox+tip,y: top+29)); c.closePath(); c.fillPath()
            c.fill(CGRect(x: ox+bar,y: top+16,width: 2.5,height: 26))
        }
    }
}
