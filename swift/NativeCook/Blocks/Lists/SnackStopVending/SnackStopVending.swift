import SwiftUI
import UIKit
import CoreText
import CoreImage

/// A twenty-item product list, rendered in the reference's 393 × 852 point space.
struct SnackStopVending: View {
    static let loop = 8.0
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var phase
    var body: some View {
        SnackStopVendingSurface(autoPlay: autoPlay, reduced: reduced, active: phase == .active)
            .ignoresSafeArea().preferredColorScheme(.light)
    }
}

private struct SnackStopVendingSurface: UIViewRepresentable {
    var autoPlay: Bool, reduced: Bool, active: Bool
    func makeUIView(context: Context) -> SnackStopVendingView { SnackStopVendingView() }
    func updateUIView(_ v: SnackStopVendingView, context: Context) { v.configure(autoPlay, reduced, active) }
    static func dismantleUIView(_ v: SnackStopVendingView, coordinator: ()) { v.stop() }
}

/// UIKit supplies the display clock, baseline text and real accessibility actions; SwiftUI owns lifecycle.
private final class SnackStopVendingView: UIView, UIGestureRecognizerDelegate {
    typealias M = SnackStopVendingMotion
    private let art = SnackStopVendingArt()
    private var touch = M.Touch(), clock = 0.0, lastBeat = -1.0
    private var origin = CACurrentMediaTime(), pausedAt: Double?
    private var autoPlay = false, reduced = false, active = true
    private var link: CADisplayLink?
    private var cachedBase: UIImage?, cachedSize = CGSize.zero
    private var actions: [UIView] = []
    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = true; backgroundColor = UIColor(red: 1, green: 0.84, blue: 0.75, alpha: 1)
        let tap = UITapGestureRecognizer(target: self, action: #selector(tapped(_:)))
        tap.delegate = self; addGestureRecognizer(tap)
        isAccessibilityElement = false; accessibilityViewIsModal = true
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        var target = touch.view
        while let v = target, v !== self { if v is UIControl { return false }; target = v.superview }
        return true
    }
    func configure(_ autoplay: Bool, _ reduce: Bool, _ foreground: Bool) {
        if reduced != reduce { touch = M.Touch(); touch.loopStart = now(); lastBeat = -1 }
        autoPlay = autoplay
        reduced = reduce
        if !autoPlay && !touch.manual { touch.manual = true }
        if active != foreground {
            if foreground, let p = pausedAt { origin += CACurrentMediaTime()-p; pausedAt = nil }
            else if !foreground { pausedAt = CACurrentMediaTime() }
        }
        active = foreground; wake(); setNeedsDisplay()
    }
    private func now() -> Double { (pausedAt ?? CACurrentMediaTime())-origin }
    func stop() { link?.invalidate(); link = nil }
    override func didMoveToWindow() { super.didMoveToWindow(); if window == nil { stop() } else { wake() } }
    private func wake() {
        guard active, window != nil else { stop(); return }
        if link == nil {
            let l = CADisplayLink(target: self, selector: #selector(tick))
            l.preferredFrameRateRange = CAFrameRateRange(minimum: 30, maximum: 120, preferred: 60)
            l.add(to: .main, forMode: .common); link = l
        }
        link?.isPaused = false
    }
    @objc private func tick() {
        clock = now(); touch.step(clock, autoPlay, reduced)
        let beat = touch.manual || reduced ? touch.beat(clock, reduced) : -1
        if beat >= 3.6 && lastBeat >= 0 && lastBeat < 3.6 && touch.slot >= 0 {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            UIAccessibility.post(notification: .announcement, argument: "\(M.drinks[M.color(touch.slot)][0]) is ready in the tray")
        }
        lastBeat = beat; setNeedsDisplay()
        if !touch.moving(clock, autoPlay, reduced) { link?.isPaused = true }
    }
    private func act(_ body: (inout M.Touch, Double) -> Void) {
        clock = now(); touch.over(clock, reduced); body(&touch, clock)
        wake(); setNeedsDisplay()
    }
    @objc private func tapped(_ tap: UITapGestureRecognizer) {
        let p = tap.location(in: self), l = SnackStopVendingLayout(bounds.size)
        let key = (0..<12).first { hypot(p.x-l.key($0).x, p.y-l.key($0).y) <= 13 }
        let slot = (0..<20).first { l.slotRect($0).insetBy(dx: -4, dy: -4).contains(p) }
        act { s, c in
            if let key { s.key(key, c, reduced) }
            else if l.tray.contains(p) { s.take(c, reduced) }
            else if let slot { s.choose(slot, c, reduced) }
        }
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        guard bounds.size != cachedSize else { return }
        cachedSize = bounds.size
        cachedBase = art.base(SnackStopVendingLayout(bounds.size), scale: traitCollection.displayScale)
        let l = SnackStopVendingLayout(bounds.size)
        actions.forEach { $0.removeFromSuperview() }; actions = []
        func add(_ name: String, _ rect: CGRect, _ traits: UIAccessibilityTraits, _ action: @escaping () -> Void) {
            let e: UIView
            if traits.contains(.button) {
                let b = SnackStopVendingButton(frame: rect); b.action = action
                b.circular = name == "Clear code" || M.keys.contains(name); e = b
            }
            else { e = UIView(frame: rect); e.isUserInteractionEnabled = false }
            e.isAccessibilityElement = true; e.accessibilityLabel = name; e.accessibilityTraits = traits
            e.accessibilityIdentifier = "snack-stop-"+name
            addSubview(e); actions.append(e)
        }
        add("Snack Stop, Lobby, Level 2", CGRect(x: l.ox+28, y: 56*l.fy, width: 240, height: 50), .header) {}
        for slot in 0..<20 {
            let drink = M.drinks[M.color(slot)]
            add("\(drink[0]), \(M.code(slot)), \(drink[2])", l.slotRect(slot), .button) { [weak self] in
                guard let self else { return }; self.act { s, c in s.choose(slot, c, self.reduced) }
            }
        }
        for key in 0..<12 {
            let p = l.key(key)
            add(M.keys[key] == "✕" ? "Clear code" : M.keys[key], CGRect(x: p.x-13, y: p.y-13, width: 26, height: 26), .button) { [weak self] in
                guard let self else { return }; self.act { s, c in s.key(key, c, self.reduced) }
            }
        }
        add("Take drink from tray", l.tray, .button) { [weak self] in
            guard let self else { return }; self.act { s, c in s.take(c, self.reduced) }
        }
        add("Code display", CGRect(x: l.x(750), y: l.y(300), width: 65, height: 45), .staticText) {}
        add("Pick a snack, Tap a code on the keypad", CGRect(x: l.ox+20, y: 716*l.fy, width: 353, height: 108), .staticText) {}
        accessibilityElements = actions
    }
    override func draw(_ rect: CGRect) {
        guard let ctx = UIGraphicsGetCurrentContext() else { return }
        cachedBase?.draw(at: .zero)
        let f = touch.frame(clock, reduced)
        if actions.count == 36 {
            if actions[34].accessibilityValue != f.lcd { actions[34].accessibilityValue = f.lcd }
            var status = "Pick a snack, Tap a code on the keypad"
            if f.card && f.cardSlot >= 0 {
                let drink = M.drinks[M.color(f.cardSlot)]
                status = "\(drink[0]), \(drink[1]) · \(M.code(f.cardSlot)), \(f.pill == 2 ? "Enjoy!" : drink[2])"
            }
            if actions[35].accessibilityLabel != status { actions[35].accessibilityLabel = status }
        }
        art.draw(ctx, SnackStopVendingLayout(bounds.size), f)
    }
}

private final class SnackStopVendingButton: UIButton {
    var action: (() -> Void)?
    var circular = false
    override init(frame: CGRect) {
        super.init(frame: frame)
        addTarget(self, action: #selector(pressed), for: .touchDown)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    @objc private func pressed() { action?() }
    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        super.point(inside: point, with: event) && (!circular || hypot(point.x-bounds.midX, point.y-bounds.midY) <= 13)
    }
    override func accessibilityActivate() -> Bool { action?(); return true }
}

private struct SnackStopVendingLayout {
    static let k = 360.0/930, cw = 92*k, ch = cw*159/96
    private static let coilX = [191.0,306,419,530,642], shelfY = [416.0,605,791,974]
    private static let keyX = [766.0,832,897], keyY = [497.0,567,638,710]
    let size: CGSize, ox: Double, fy: Double, gy: Double
    init(_ size: CGSize) { self.size = size; ox = (size.width-393)/2; fy = size.height/852; gy = 110*(fy-1) }
    func x(_ v: Double) -> Double { ox+16.5+(v-45)*Self.k }
    func y(_ v: Double) -> Double { gy+110+(v-105)*Self.k }
    func slot(_ s: Int) -> CGPoint { CGPoint(x: x(Self.coilX[s%5]), y: y(Self.shelfY[s/5])) }
    func key(_ k: Int) -> CGPoint { CGPoint(x: x(Self.keyX[k%3]), y: y(Self.keyY[k/3])) }
    func slotRect(_ s: Int) -> CGRect { let p = slot(s); return CGRect(x: p.x-Self.cw/2, y: p.y-Self.ch, width: Self.cw, height: Self.ch) }
    var tray: CGRect { CGRect(x: x(135), y: y(1150), width: x(712)-x(135), height: y(1335)-y(1150)) }
    func trayX(_ s: Int) -> Double { min(max(slot(s).x-40, tray.minX+4), tray.maxX-Self.ch*1.12-4) }
}

private final class SnackStopVendingArt {
    typealias L = SnackStopVendingLayout
    typealias M = SnackStopVendingMotion
    private let machine: UIImage, cans: [UIImage], fonts: [String: UIFont]
    private var lines: [String: CTLine] = [:]
    private let coils: [CGPath]
    private var shadow: UIImage?
    init() {
        func image(_ name: String) -> UIImage {
            guard let path = Bundle.main.path(forResource: "SnackStopVending-"+name, ofType: "webp"), let im = UIImage(contentsOfFile: path) else { return UIImage() }
            return im.preparingForDisplay() ?? im
        }
        machine = image("machine"); cans = (0..<5).map { image("can\($0)") }
        var loaded: [String: UIFont] = [:]
        for (key, file, size) in [("title","TitanOne-Regular",30.0),("sub","Inter-Medium",14),("name","Onest-ExtraBold",20),("pill","Onest-ExtraBold",22),("key","Inter-SemiBold",9),("lcd","JetBrainsMono-Medium",15)] {
            if let url = Bundle.main.url(forResource: "SnackStopVending-"+file, withExtension: "ttf"), let provider = CGDataProvider(url: url as CFURL), let cg = CGFont(provider) {
                CTFontManagerRegisterGraphicsFont(cg, nil)
                loaded[key] = UIFont(name: cg.postScriptName! as String, size: size)
            }
        }
        fonts = loaded
        coils = (0..<2).map { n in
            let p = CGMutablePath()
            let transform = CGAffineTransform(translationX: 0, y: -8-Double(n)*9).scaledBy(x: L.cw*0.66, y: 4.5)
            p.addArc(center: .zero, radius: 1, startAngle: 0.15, endAngle: .pi-0.15, clockwise: false, transform: transform)
            return p
        }
    }
    private func color(_ hex: UInt32, _ alpha: Double = 1) -> UIColor {
        UIColor(red: Double((hex>>16)&255)/255, green: Double((hex>>8)&255)/255, blue: Double(hex&255)/255, alpha: alpha)
    }
    private func text(_ ctx: CGContext, _ value: String, _ face: String, _ x: Double, _ y: Double, _ ink: UInt32, _ center: Bool = false, _ alpha: Double = 1) {
        let key = face+value+String(ink)+String(alpha)
        let line: CTLine
        if let found = lines[key] { line = found }
        else {
            let attrs: [NSAttributedString.Key: Any] = [.font: fonts[face] ?? UIFont.systemFont(ofSize: 14), .foregroundColor: color(ink, alpha)]
            line = CTLineCreateWithAttributedString(NSAttributedString(string: value, attributes: attrs)); lines[key] = line
        }
        let width = CTLineGetTypographicBounds(line, nil, nil, nil)
        ctx.saveGState(); ctx.translateBy(x: x-(center ? width/2 : 0), y: y); ctx.scaleBy(x: 1, y: -1)
        ctx.textMatrix = .identity; ctx.textPosition = .zero; CTLineDraw(line, ctx); ctx.restoreGState()
    }
    func base(_ l: L, scale: Double) -> UIImage {
        let format = UIGraphicsImageRendererFormat(); format.scale = scale; format.opaque = true
        if shadow == nil {
            let maskFormat = UIGraphicsImageRendererFormat(); maskFormat.scale = scale
            let mask = UIGraphicsImageRenderer(size: CGSize(width: 380,height: 70), format: maskFormat).image { r in
                r.cgContext.setFillColor(color(0x962814,0.25).cgColor)
                r.cgContext.fillEllipse(in: CGRect(x: 20,y: 21,width: 340,height: 28))
            }
            if let input = CIImage(image: mask) {
                let blurred = input.applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: 14/3*scale])
                if let cg = CIContext().createCGImage(blurred, from: input.extent) { shadow = UIImage(cgImage: cg, scale: scale, orientation: .up) }
            }
        }
        return UIGraphicsImageRenderer(size: l.size, format: format).image { r in
            let c = r.cgContext
            let gradient = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: [color(0xFFE2CF).cgColor,color(0xFFC9AE).cgColor] as CFArray, locations: [0,1])!
            c.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: 0, y: l.size.height), options: [])
            shadow?.draw(at: CGPoint(x: l.ox+6.5,y: l.gy+617))
            machine.draw(in: CGRect(x: l.ox+16.5, y: l.gy+110, width: 360, height: 1382*L.k))
            let card = CGRect(x: l.ox+20,y: 716*l.fy,width: 353,height: 108)
            c.saveGState(); c.setShadow(offset: CGSize(width: 0,height: 10/3), blur: 10,color: color(0xA02814,0.18).cgColor)
            c.setFillColor(UIColor.white.cgColor); c.addPath(UIBezierPath(roundedRect: card,cornerRadius: 28).cgPath); c.fillPath(); c.restoreGState()
        }
    }
    private func can(_ c: CGContext, _ image: UIImage, _ p: CGPoint, _ scale: Double = 1, _ rot: Double = 0, _ alpha: Double = 1) {
        guard alpha > 0 else { return }
        c.saveGState(); c.translateBy(x: p.x, y: p.y); c.rotate(by: rot); c.setAlpha(alpha)
        image.draw(in: CGRect(x: -L.cw*scale/2, y: -L.ch*scale, width: L.cw*scale, height: L.ch*scale)); c.restoreGState()
    }
    private func coil(_ c: CGContext, _ p: CGPoint) {
        c.saveGState(); c.translateBy(x: p.x, y: p.y); c.setStrokeColor(color(0xE1E4E8, 0.85).cgColor); c.setLineWidth(1.3)
        for path in coils { c.addPath(path); c.strokePath() }; c.restoreGState()
    }
    func draw(_ c: CGContext, _ l: L, _ f: M.Frame) {
        for s in 0..<20 {
            if s == f.slot && f.can.kind != 0 { continue }
            let p = l.slot(s); can(c, cans[M.color(s)], p)
            if s != f.slot { coil(c, p) }
        }
        if f.slot >= 0 {
            let p = l.slot(f.slot), image = cans[M.color(f.slot)], a = f.can, floor = l.tray.maxY-6
            if a.kind == 2 { can(c, image, p, a.scale, 0, a.alpha) }
            if a.kind == 1 {
                let x = p.x+(l.trayX(f.slot)-p.x)*a.fall
                let y = a.fall >= 1 ? floor-a.bounce+a.taken : p.y+(floor-p.y)*a.drop
                c.saveGState(); if a.clip { c.clip(to: l.tray.inset(by: UIEdgeInsets(top: 2, left: 0, bottom: 0, right: 0))) }
                can(c, image, CGPoint(x: x, y: y), a.scale, a.rot, a.alpha); c.restoreGState()
            }
            if f.trayAlpha > 0 {
                c.saveGState(); c.clip(to: l.tray.inset(by: UIEdgeInsets(top: 2, left: 0, bottom: 0, right: 0)))
                can(c, image, CGPoint(x: l.trayX(f.slot), y: floor), 1.12, .pi/2, f.trayAlpha); c.restoreGState()
            }
            if a.veil || f.trayAlpha > 0 { c.setFillColor(UIColor.white.withAlphaComponent(0.1).cgColor); c.fill(CGRect(x: l.tray.minX, y: l.tray.minY, width: l.tray.width, height: l.tray.height*0.55)) }
            coil(c, p)
            if f.glint >= 0 {
                c.setStrokeColor(UIColor.white.withAlphaComponent(0.9*sin(f.glint * .pi)).cgColor); c.setLineWidth(2)
                c.addArc(center: CGPoint(x: p.x, y: p.y-12), radius: L.cw*0.7, startAngle: f.glint * .pi*4, endAngle: f.glint * .pi*4+1.1, clockwise: false); c.strokePath()
            }
        }
        for (key,u) in [(f.flashA,f.flashAU),(f.flashB,f.flashBU)] where key >= 0 && u >= 0 {
            let p = l.key(key); c.setFillColor(color(0x7CFF8A, 0.55*sin(u * .pi)).cgColor); c.fillEllipse(in: CGRect(x: p.x-10, y: p.y-10, width: 20, height: 20))
        }
        for key in 0..<12 {
            let p = l.key(key)
            if key == 11 {
                c.setStrokeColor(color(0x7A4A3A,0.85).cgColor); c.setLineWidth(1.2); c.setLineCap(.round)
                c.move(to: CGPoint(x: p.x-2.8,y: p.y-2.8)); c.addLine(to: CGPoint(x: p.x+2.8,y: p.y+2.8))
                c.move(to: CGPoint(x: p.x-2.8,y: p.y+2.8)); c.addLine(to: CGPoint(x: p.x+2.8,y: p.y-2.8)); c.strokePath(); c.setLineCap(.butt)
            } else { text(c,M.keys[key],"key",p.x,p.y+3.2,0x7A4A3A,true,0.85) }
        }
        text(c,f.lcd,"lcd",l.x(837)+f.lcdShake,l.y(346)+5,0x7CFF8A,true)
        if f.finger > 0 { let p = l.key(f.fingerKey); c.setFillColor(color(0x28140A,0.22*f.finger).cgColor); c.fillEllipse(in: CGRect(x: p.x-20,y: p.y-20,width: 40,height: 40)) }
        text(c,"Snack Stop","title",l.ox+28,82*l.fy,0xC4142B)
        text(c,"Lobby · Level 2","sub",l.ox+29,102*l.fy,0x8A5A48)
        let top = 716*l.fy
        let d = f.card && f.cardSlot >= 0 ? M.drinks[M.color(f.cardSlot)] : nil
        text(c,d?[0] ?? "Pick a snack","name",l.ox+42,top+40,0x2B1A14)
        text(c,d.map { $0[1]+" · "+M.code(f.cardSlot) } ?? "Tap a code on the keypad","sub",l.ox+42,top+66,0x9A7A6A)
        let pillTop = 736*l.fy
        c.setFillColor(color(f.pill == 2 ? 0x2E9E55 : f.pill == 1 ? 0xC4142B : 0xE8D8CF).cgColor)
        c.addPath(UIBezierPath(roundedRect: CGRect(x: l.ox+245,y: pillTop,width: 110,height: 66),cornerRadius: 33).cgPath); c.fillPath()
        text(c,f.pill == 2 ? "Enjoy!" : f.pill == 1 ? d?[2] ?? "—" : "—","pill",l.ox+300,pillTop+41,f.pill == 0 ? 0x9A7A6A : 0xFFFFFF,true)
    }
}
