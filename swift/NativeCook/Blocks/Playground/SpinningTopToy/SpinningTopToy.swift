import SwiftUI
import MetalKit
import CoreText

struct SpinningTopToy: View {
    static let loop = 7.6
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scene
    var body: some View {
        SpinningTopToySurface(auto: autoPlay, reduced: reduced, active: scene == .active)
            .ignoresSafeArea().preferredColorScheme(.light)
    }
}
private struct SpinningTopToySurface: UIViewRepresentable {
    let auto: Bool, reduced: Bool, active: Bool
    func makeUIView(context: Context) -> SpinningTopToyView { SpinningTopToyView() }
    func updateUIView(_ v: SpinningTopToyView, context: Context) { v.configure(auto,reduced,active) }
    static func dismantleUIView(_ v: SpinningTopToyView, coordinator: ()) { v.stop() }
}
private final class SpinningTopToyOverlay: UIView {
    weak var owner: SpinningTopToyView?
    override func draw(_ rect: CGRect) { if let c=UIGraphicsGetCurrentContext() { owner?.drawCopy(c) } }
}
private final class SpinningTopToyLink: NSObject {
    weak var owner: SpinningTopToyView?
    @objc func tick(_ link: CADisplayLink) { owner?.advance(link.timestamp) }
}
private final class SpinningTopToyButton: UIButton {
    var activation: (() -> Void)?
    override func accessibilityActivate() -> Bool { activation?(); return true }
}
final class SpinningTopToyView: UIView, UIGestureRecognizerDelegate {
    let motion=SpinningTopToyMotion()
    private let renderer=try! SpinningTopToyRenderer()
    private var metal: MTKView!
    private let overlay=SpinningTopToyOverlay(), target=SpinningTopToyLink()
    private let top=SpinningTopToyButton(type:.custom), pill=SpinningTopToyButton(type:.custom)
    private var link: CADisplayLink?, auto=false, active=true, observers:[NSObjectProtocol]=[]
    private var flicksSeen=0, landsSeen=0, panDown=false
    private struct Line { let line: CTLine; let width: Double }
    private var fonts:[String:CTFont]=[:], timers:[Line]=[], keeps:[Line]=[], downs:[Line]=[], bests:[Line]=[]
    private var eyebrow: Line!, unit: Line!, pillCopy: Line!
    override init(frame: CGRect) {
        super.init(frame:frame); isOpaque=true; clipsToBounds=true
        backgroundColor=UIColor(red:142/255,green:135/255,blue:128/255,alpha:1)
        metal=MTKView(frame:.zero,device:renderer.device); metal.colorPixelFormat = .bgra8Unorm
        (metal.layer as? CAMetalLayer)?.colorspace=CGColorSpace(name:CGColorSpace.sRGB)
        metal.isPaused=true; metal.enableSetNeedsDisplay=false; metal.isUserInteractionEnabled=false
        addSubview(metal)
        overlay.owner=self; overlay.backgroundColor = .clear; overlay.isOpaque=false; overlay.isUserInteractionEnabled=false
        addSubview(overlay)
        for face in ["DMSerifDisplay-Regular","Inter-Medium","Inter-SemiBold"] {
            let url=Bundle.main.url(forResource:"SpinningTopToy-"+face,withExtension:"ttf")!
            fonts[face]=CTFontCreateWithGraphicsFont(CGFont(CGDataProvider(url:url as CFURL)!)!,1,nil,nil)
        }
        for i in 0..<100 {
            let s=String(format:"%.1f",Double(i)/10)
            timers.append(line(s,"DMSerifDisplay-Regular",96,0x17130F,-3))
            keeps.append(line("Flick it. Keep it up longer than \(s) s.","Inter-Medium",15,0x2F2925))
            downs.append(line("Down after \(s) s. Flick again.","Inter-Medium",15,0x2F2925))
            bests.append(line("Best · \(s) s","Inter-SemiBold",13,0x2B2622))
        }
        eyebrow=line("SPIN · DESK TOYS","Inter-SemiBold",12,0x3A322C,4)
        unit=line("s","DMSerifDisplay-Regular",44,0x17130F)
        pillCopy=line("Flick to spin","Inter-SemiBold",17,0xFFFFFF)
        top.accessibilityLabel="Spin the top"; top.accessibilityIdentifier="spinning-top-toy-top"
        pill.accessibilityLabel="Flick to spin"; pill.accessibilityIdentifier="spinning-top-toy-pill"
        top.activation={ [weak self] in self?.launch(onTop:false) }
        pill.activation=top.activation
        for b in [top,pill] { b.accessibilityTraits = .button; addSubview(b) }
        let pan=UIPanGestureRecognizer(target:self,action:#selector(pan(_:))); pan.delegate=self
        top.addGestureRecognizer(pan)
        top.addTarget(self,action:#selector(topDown),for:.touchDown)
        top.addTarget(self,action:#selector(topUp),for:.touchUpInside)
        top.addTarget(self,action:#selector(topCancel),for:[.touchCancel,.touchUpOutside])
        pill.addTarget(self,action:#selector(pillDown),for:.touchDown)
        pill.addTarget(self,action:#selector(pillUp),for:.touchUpInside)
        pill.addTarget(self,action:#selector(pillCancel),for:[.touchCancel,.touchUpOutside])
        target.owner=self
        let nc=NotificationCenter.default
        observers.append(nc.addObserver(forName:UIAccessibility.reduceMotionStatusDidChangeNotification,object:nil,queue:.main) { [weak self] _ in
            self?.motion.setStill(UIAccessibility.isReduceMotionEnabled); self?.wake()
        })
        for name in [UIApplication.didBecomeActiveNotification,UIApplication.willResignActiveNotification] {
            observers.append(nc.addObserver(forName:name,object:nil,queue:.main) { [weak self] n in
                self?.active=n.name == UIApplication.didBecomeActiveNotification; self?.motion.last = -1; self?.wake()
            })
        }
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    deinit { link?.invalidate(); observers.forEach(NotificationCenter.default.removeObserver) }
    func stop() { link?.invalidate(); link=nil }
    func configure(_ auto: Bool,_ reduced: Bool,_ active: Bool) { self.auto=auto; self.active=active; motion.setStill(reduced); wake() }
    override func didMoveToWindow() { super.didMoveToWindow(); motion.last = -1; wake() }
    func wake() {
        if link == nil {
            let l=CADisplayLink(target:target,selector:#selector(SpinningTopToyLink.tick(_:)))
            l.preferredFrameRateRange=CAFrameRateRange(minimum:60,maximum:120,preferred:120)
            l.add(to:.main,forMode:.common); link=l
        }
        link?.isPaused=window == nil || !active
    }
    override func layoutSubviews() {
        super.layoutSubviews(); metal.frame=bounds; overlay.frame=bounds
        top.frame=CGRect(x:62,y:288,width:294,height:318)
        pill.frame=CGRect(x:(bounds.width-329)/2,y:799*bounds.height/852-29,width:329,height:58)
        render()
    }
    func advance(_ now: Double) {
        motion.step(now,auto:auto)
        if motion.flicks != flicksSeen {
            flicksSeen=motion.flicks
            UIImpactFeedbackGenerator(style:.light).impactOccurred()
            UIAccessibility.post(notification:.announcement,argument:"Spinning")
        }
        if motion.lands != landsSeen {
            landsSeen=motion.lands
            UIImpactFeedbackGenerator(style:.soft).impactOccurred()
            UIAccessibility.post(notification:.announcement,argument:String(format:"Down after %.1f seconds",motion.duration))
        }
        render()
        // Pause only once the authored rock has decayed and no idle transition is pending.
        if motion.mode == 1 && (motion.still || !auto) && motion.touches == 0 &&
            (motion.phase == 0 || (!auto && motion.phase == 1 && motion.tau >= motion.duration+2.2 && motion.bestAge >= 0.3)) {
            link?.isPaused=true; motion.last = -1
        }
    }
    func render() { renderer.draw(metal,motion.frame); overlay.setNeedsDisplay() }
    func launch(onTop: Bool) { motion.spin(onTop:onTop); wake() }
    @objc private func topDown() { motion.touchDown(); wake() }
    @objc private func topUp() { motion.spin(onTop:true); motion.touchUp(); wake() }
    @objc private func topCancel() { motion.touchUp(); wake() }
    @objc private func pillDown() { motion.touchDown(pill:true); wake() }
    @objc private func pillUp() { motion.spin(); motion.touchUp(); wake() }
    @objc private func pillCancel() { motion.touchUp(); wake() }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
    @objc private func pan(_ g: UIPanGestureRecognizer) {
        switch g.state {
        case .began: motion.touchDown(); panDown=true; wake()
        case .ended:
            let v=g.velocity(in:self),d=g.translation(in:self),speed=hypot(v.x,v.y)
            motion.spin(hypot(d.x,d.y) >= 12 && speed >= 300 ? speed : 0,onTop:true)
            if panDown { motion.touchUp(); panDown=false }; wake()
        case .cancelled,.failed: if panDown { motion.touchUp(); panDown=false }; wake()
        default: break
        }
    }
    private func color(_ h: Int) -> UIColor { UIColor(red:Double((h>>16)&255)/255,green:Double((h>>8)&255)/255,blue:Double(h&255)/255,alpha:1) }
    private func line(_ text: String,_ face: String,_ size: Double,_ hex: Int,_ tracking: Double = 0) -> Line {
        let font=CTFontCreateCopyWithAttributes(fonts[face]!,size,nil,nil)
        let attrs:[NSAttributedString.Key:Any]=[.font:font,.foregroundColor:color(hex),.kern:tracking]
        let l=CTLineCreateWithAttributedString(NSAttributedString(string:text,attributes:attrs))
        return Line(line:l,width:CTLineGetTypographicBounds(l,nil,nil,nil))
    }
    private func draw(_ c: CGContext,_ l: Line,_ x: Double,_ y: Double) {
        c.saveGState(); c.translateBy(x:x,y:y); c.scaleBy(x:1,y:-1); c.textPosition = .zero; CTLineDraw(l.line,c); c.restoreGState()
    }
    func drawCopy(_ c: CGContext) {
        let f=motion.frame,sy=bounds.height/852,cx=bounds.width/2,tx=cx-12
        func index(_ s: Double) -> Int { min(99,max(0,Int((s*10).rounded()))) }
        draw(c,eyebrow,cx-eyebrow.width/2,90*sy)
        let timer=timers[index(f.seconds)]
        draw(c,timer,tx-timer.width/2,172*sy); draw(c,unit,tx+timer.width/2+4,172*sy)
        let copy=f.down ? downs[index(f.seconds)] : keeps[index(f.best)]
        draw(c,copy,cx-copy.width/2,214*sy)
        c.saveGState(); c.translateBy(x:cx,y:700*sy+15); c.scaleBy(x:f.bestPop,y:f.bestPop)
        c.setFillColor(UIColor.white.withAlphaComponent(0.5).cgColor)
        c.addPath(UIBezierPath(roundedRect:CGRect(x:-57.5,y:-15,width:115,height:30),cornerRadius:15).cgPath); c.fillPath()
        let best=bests[index(f.best)]; draw(c,best,-best.width/2,4); c.restoreGState()
        c.saveGState(); c.translateBy(x:cx,y:799*sy); let scale=f.pill ? 0.97 : 1
        c.scaleBy(x:scale,y:scale); c.setFillColor(color(0x141210).cgColor)
        c.addPath(UIBezierPath(roundedRect:CGRect(x:-164.5,y:-29,width:329,height:58),cornerRadius:29).cgPath); c.fillPath()
        draw(c,pillCopy,-pillCopy.width/2,6); c.restoreGState()
    }
}
