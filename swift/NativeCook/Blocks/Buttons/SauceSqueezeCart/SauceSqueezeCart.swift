import SwiftUI
import UIKit

/*
 Sauce Squeeze Cart — the add-to-cart moment of an invented grocery app ("Pantry Run").

 Look: a pink product page for "Tomato Ketchup" by an invented brand, PLUMPO. A glossy red squeeze
 bottle, drawn in code (a pillowy body with three bulges per side, a white two-tier cap pointing down,
 a label with a tomato), floats over a cream card with the name, chips, the price and "Hold to add".

 Hero: holding squeezes the bottle (squash and stretch, a trembling waist, a bead at the nozzle);
 releasing shoots a thick glossy stream that whips down the screen while a red pool rises until the
 whole screen is sauce. Stickers pop on the red ("IN YOUR CART", a smiley, a cart, "+1 ADDED", an
 arrow, a sparkle), then the red retracts up the screen with drips hanging from it, the page comes
 back and the cart in the header bumps with a "+1". SauceSqueezeCartMotion.swift has the timeline.

 autoPlay: loop = 8 s (rest, squeeze, shoot and flood, stickers, drain, rest).
 Touch: press and hold the bottle or the button; the longer the hold, the harder the squeeze. Release
 past 40 % to shoot, below it the bottle springs back. A light haptic when the squeeze is armed, at
 the release and when the screen is red. 2 s after the last touch (and once a shot has played out)
 the loop restarts.
 Reduced motion: the page still; a full hold fades the red and its stickers in and out.
*/
struct SauceSqueezeCart: View {
    /// Plays the 8 s squeeze-and-flood loop until touched.
    var autoPlay = false

    typealias M = SauceSqueezeCartMotion
    typealias A = SauceSqueezeCartArt

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var driver = SauceSqueezeCartDriver()
    @State private var live = false
    @State private var token = 0
    @State private var grabbed: Bool?

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(A.color(A.pink).ignoresSafeArea())
        .preferredColorScheme(.light)
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let g = SauceSqueezeCartGeo(w: max(1, size.width), h: max(1, size.height),
                                    top: insets.top, bottom: insets.bottom)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        return TimelineView(.animation(paused: !auto && !live)) { timeline in
            let now = timeline.date.timeIntervalSinceReferenceDate
            let f = driver.frame(at: now, auto: auto, reduced: reduced)
            Canvas { c, _ in
                Self.draw(&c, g, f)
            }
            .frame(width: size.width, height: size.height)
        }
        .contentShape(Rectangle())
        .gesture(touch(g, auto: auto, reduced: reduced))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Tomato Ketchup, 3 dollars 80")
        .accessibilityHint("Hold to squeeze the bottle and add it to your cart.")
        .accessibilityAddTraits(.isButton)
        .accessibilityAction {
            let now = Date().timeIntervalSinceReferenceDate
            driver.shoot(at: now, auto: auto, reduced: reduced)
            schedule(cover: true)
        }
        .onChange(of: auto) { driver.reset() }
    }

    // MARK: Drawing

    static let cream: UInt32 = 0xFFF4FA

    static func draw(_ c: inout GraphicsContext, _ g: SauceSqueezeCartGeo, _ f: SauceSqueezeCartFrame) {
        c.fill(Path(CGRect(x: 0, y: 0, width: g.w, height: g.h)), with: .color(A.color(A.pink)))
        header(&c, g, f)
        card(&c, g, f)

        // The bottle.
        let center = g.bottleCenter(f)
        let angle = g.bottleAngle(f)
        var b = c
        b.translateBy(x: center.x, y: center.y)
        b.rotate(by: .degrees(angle))
        b.scaleBy(x: g.bottleScale, y: g.bottleScale)
        A.bottle(&b, f)
        if f.tau == nil && !f.reduced { A.bead(&b, f.s) }

        guard let tau = f.tau else { return }
        if f.reduced {
            let a = M.redFade(tau)
            if a > 0.001 {
                c.fill(Path(CGRect(x: 0, y: 0, width: g.w, height: g.h)), with: .color(A.color(A.sauce, a)))
            }
        } else {
            let root = g.nozzle(f)
            if M.streamVisible(tau) {
                A.drops(&c, root: root, tau: tau)
                A.stream(&c, root: root, tau: tau, h: g.h)
            }
            A.red(&c, tau: tau, w: g.w, h: g.h)
        }
        A.stickers(&c, tau: tau, cx: g.cx, cy: g.stickerY, reduced: f.reduced)
    }

    static func header(_ c: inout GraphicsContext, _ g: SauceSqueezeCartGeo, _ f: SauceSqueezeCartFrame) {
        let y = g.top + 30
        let plum = A.color(A.plum)
        c.fill(A.circle(42, y, 22), with: .color(.white.opacity(0.55)))
        var chev = Path()
        chev.move(to: CGPoint(x: 45, y: y - 7))
        chev.addLine(to: CGPoint(x: 38, y: y))
        chev.addLine(to: CGPoint(x: 45, y: y + 7))
        c.stroke(chev, with: .color(plum), style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
        c.draw(Text("Pantry Run").font(.system(size: 16, weight: .bold)).foregroundColor(plum),
               at: CGPoint(x: g.cx, y: y), anchor: .center)

        // Cart: bumps when the sauce lands in it, a "+1" floats up.
        let cx = g.w - 42
        let bump = f.tau.map { M.cartBump($0) } ?? 0
        let k = 1 + 0.6 * bump
        var cart = c
        cart.translateBy(x: cx, y: y)
        cart.scaleBy(x: k, y: k)
        cart.fill(A.circle(0, 0, 22), with: .color(.white.opacity(0.55)))
        A.cart(&cart, 0, 0, 0.95, plum)

        if let tau = f.tau {
            let p = M.plusOne(tau)
            if p.scale > 0.01 && p.alpha > 0.01 {
                var l = c
                l.opacity = p.alpha
                l.translateBy(x: cx - 26, y: y - 12 - p.rise)
                l.scaleBy(x: p.scale, y: p.scale)
                l.fill(A.roundRect(-17, -11, 34, 22, 11), with: .color(A.color(A.sauce)))
                l.draw(Text("+1").font(.system(size: 13, weight: .heavy)).foregroundColor(.white),
                       at: .zero, anchor: .center)
            }
        }
    }

    static func card(_ c: inout GraphicsContext, _ g: SauceSqueezeCartGeo, _ f: SauceSqueezeCartFrame) {
        let top = g.cardY
        let plum = A.color(A.plum)
        let muted = A.color(A.mutedPlum)
        let shape = UnevenRoundedRectangle(topLeadingRadius: 34, topTrailingRadius: 34, style: .continuous)
            .path(in: CGRect(x: 0, y: top, width: g.w, height: g.h - top + 2))
        c.fill(shape, with: .color(A.color(cream)))

        c.draw(Text("PLUMPO · 500 ML").font(.system(size: 12, weight: .semibold)).tracking(1.4).foregroundColor(muted),
               at: CGPoint(x: 24, y: top + 34), anchor: .leading)
        c.draw(Text("Tomato Ketchup").font(.system(size: 30, weight: .heavy)).foregroundColor(plum),
               at: CGPoint(x: 24, y: top + 68), anchor: .leading)
        c.draw(Text("Sun-ripe, slow-cooked, a pinch of smoke.").font(.system(size: 15))
                .foregroundColor(A.color(0x7A4A70)),
               at: CGPoint(x: 24, y: top + 100), anchor: .leading)

        var x = 24.0
        for chip in ["★ 4.8", "Vegan", "No added sugar"] {
            let text = c.resolve(Text(chip).font(.system(size: 13, weight: .semibold)).foregroundColor(A.color(0x5B1450)))
            let w = text.measure(in: CGSize(width: 300, height: 40)).width + 24
            c.fill(A.roundRect(x, top + 125, w, 30, 15), with: .color(A.color(0xF9DDF0)))
            c.draw(text, at: CGPoint(x: x + w / 2, y: top + 140), anchor: .center)
            x += w + 8
        }

        let cy = g.ctaY
        c.draw(Text("$3.80").font(.system(size: 28, weight: .heavy)).foregroundColor(plum),
               at: CGPoint(x: 24, y: cy - 9), anchor: .leading)
        c.draw(Text("per bottle").font(.system(size: 12)).foregroundColor(muted),
               at: CGPoint(x: 24, y: cy + 17), anchor: .leading)

        // "Hold to add": pressed in, a darker fill grows from the left with the squeeze.
        let k = 1 - 0.04 * f.press
        var b = c
        b.translateBy(x: g.ctaX + g.ctaW / 2, y: cy)
        b.scaleBy(x: k, y: k)
        let pill = A.roundRect(-g.ctaW / 2, -30, g.ctaW, 60, 30)
        var sh = b
        sh.addFilter(.shadow(color: A.color(0x9A0010, 0.28), radius: 12, x: 0, y: 8 * (1 - 0.5 * f.press)))
        sh.fill(pill, with: .color(A.color(A.sauce)))
        var fill = b
        fill.clip(to: pill)
        fill.fill(Path(CGRect(x: -g.ctaW / 2, y: -30, width: g.ctaW * M.clamp01(f.s), height: 60)),
                  with: .color(A.color(0xB80002)))
        fill.stroke(pill.offsetBy(dx: 0, dy: 1.5), with: .color(.white.opacity(0.18)), lineWidth: 1.5)
        b.draw(Text("Hold to add").font(.system(size: 18, weight: .bold)).foregroundColor(.white),
               at: .zero, anchor: .center)
    }

    // MARK: Touch

    private func bump() {
        live = true
        token += 1
        let mine = token
        DispatchQueue.main.asyncAfter(deadline: .now() + 6) {
            if mine == token && !driver.touching { live = false }
        }
    }

    private func haptic() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    /// Haptics for the moments that come later: the squeeze armed, the screen gone red.
    private func schedule(arm: Double? = nil, cover: Bool = false) {
        bump()
        let mine = token
        if let arm {
            DispatchQueue.main.asyncAfter(deadline: .now() + arm) {
                if mine == token && driver.touching { haptic() }
            }
        }
        if cover {
            DispatchQueue.main.asyncAfter(deadline: .now() + M.coveredAt) {
                if mine == token { haptic() }
            }
        }
    }

    private func touch(_ g: SauceSqueezeCartGeo, auto: Bool, reduced: Bool) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                guard grabbed == nil else { return }
                let now = Date().timeIntervalSinceReferenceDate
                guard g.hit(v.startLocation) else { grabbed = false; return }
                grabbed = true
                if driver.begin(at: now, auto: auto, reduced: reduced) {
                    let d = driver.armDelay()
                    schedule(arm: d > 0 ? d : nil)
                } else {
                    bump()
                }
            }
            .onEnded { _ in
                defer { grabbed = nil }
                guard grabbed == true else { return }
                let now = Date().timeIntervalSinceReferenceDate
                if driver.end(at: now) {
                    haptic()
                    schedule(cover: true)
                } else {
                    bump()
                }
            }
    }
}

struct SauceSqueezeCartGeo {
    var w = 393.0
    var h = 852.0
    var top = 59.0
    var bottom = 34.0

    let ctaW = 200.0
    let bottleScale = 1.12

    var cx: Double { w / 2 }
    var cardY: Double { h - 318 }
    var ctaY: Double { h - max(bottom, 16) - 40 }
    var ctaX: Double { w - 24 - ctaW }
    var bottleY: Double { top + 250 }
    /// The stickers gather around the middle of the red screen.
    var stickerY: Double { (top + h) / 2 - 30 }

    func bottleCenter(_ f: SauceSqueezeCartFrame) -> CGPoint {
        CGPoint(x: cx + f.shake, y: bottleY + f.lift + 3 * f.bob)
    }

    func bottleAngle(_ f: SauceSqueezeCartFrame) -> Double { -7 + f.spin + 1.5 * f.sway }

    /// The nozzle's tip on the screen.
    func nozzle(_ f: SauceSqueezeCartFrame) -> CGPoint {
        let c = bottleCenter(f)
        let a = bottleAngle(f) * Double.pi / 180
        let d = (SauceSqueezeCartArt.nozzleY - 2) * bottleScale
        return CGPoint(x: c.x - sin(a) * d, y: c.y + cos(a) * d)
    }

    /// The bottle and the button take the squeeze.
    func hit(_ p: CGPoint) -> Bool {
        let bottle = CGRect(x: cx - 120, y: bottleY - 150, width: 240, height: 330)
        let cta = CGRect(x: ctaX - 12, y: ctaY - 42, width: ctaW + 24, height: 84)
        return bottle.contains(p) || cta.contains(p)
    }
}
