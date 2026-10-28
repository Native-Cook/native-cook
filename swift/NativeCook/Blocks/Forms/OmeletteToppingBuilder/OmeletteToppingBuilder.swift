import SwiftUI
import UIKit

/*
 Omelette Topping Builder — step 2 of an invented breakfast-ordering app ("Early Fold"): fold
 fillings into your omelette.

 Look: a light grey screen, a title bar ("Omelette", a round back button, "Step 2 of 3"), a white
 ceramic plate on a pale placemat with a folded yellow omelette, all drawn in code. Below, a shelf
 of five ingredients in a ring (Red Bell Pepper, Baby Spinach, Smoked Bacon, Cherry Tomato, Button
 Mushroom), the centred one on a white disc with its name and add-on price, and a dark "Continue"
 button carrying the total.

 Hero (points / scatter): the centred ingredient is tossed onto the omelette and bursts into seeded
 pieces — red pepper cubes, spinach leaves, bacon strips, tomato halves, mushroom slices — that hop
 outward, spin to their angle and land with a squash-and-bounce. They stay and accumulate; the
 omelette squashes on each impact and the total rolls up. The ingredient pops back on the shelf with
 a check, and the shelf slides on to the next one.

 autoPlay: loop = 9 s (rest, add pepper, spinach and bacon, hold, everything lifts off and the shelf
 glides back to the pepper, rest). OmeletteToppingBuilderMotion.swift has the script.
 Touch: tap the centred ingredient to add it (an added one gives a small shake); tap a side one to
 centre it; swipe the shelf; tap the plate to lift off the last topping. A light haptic when a
 topping lands and on undo. 2 s after the last touch every topping lifts off, the shelf springs back
 to the pepper and the loop restarts.
 Reduced motion: the rest frame, still; a tap fades the pieces in at their spots, without the flight.
*/
struct OmeletteToppingBuilder: View {
    /// Plays the 9 s add-three-toppings loop until touched.
    var autoPlay = false

    typealias M = OmeletteToppingBuilderMotion
    typealias A = OmeletteToppingBuilderArt
    typealias G = OmeletteToppingBuilderGeo

    static let loop = M.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var driver = OmeletteToppingBuilderDriver()
    @State private var live = false
    @State private var token = 0
    @State private var grab: Grab?

    private enum Grab {
        case ignored
        case shelf(s0: Double)
        case plate
    }

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(A.color(Self.paper).ignoresSafeArea())
        .preferredColorScheme(.light)
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let g = G(w: max(1, size.width), h: max(1, size.height), top: insets.top, bottom: insets.bottom)
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
        .accessibilityLabel("Omelette fillings")
        .accessibilityValue("Total \(Self.money(M.baseCents))")
        .accessibilityHint("Adds the centred filling to the omelette.")
        .accessibilityAddTraits(.isButton)
        .accessibilityAction {
            let now = Date().timeIntervalSinceReferenceDate
            driver.session(at: now, auto: auto, reduced: reduced).tapCentre(at: now)
            driver.touched(at: now)
            bump()
        }
        .accessibilityAction(named: "Next filling") {
            let now = Date().timeIntervalSinceReferenceDate
            driver.session(at: now, auto: auto, reduced: reduced).select(1, at: now)
            driver.touched(at: now)
            bump()
        }
        .accessibilityAction(named: "Remove last filling") {
            let now = Date().timeIntervalSinceReferenceDate
            driver.session(at: now, auto: auto, reduced: reduced).removeLast(at: now)
            driver.touched(at: now)
            bump()
        }
        .onChange(of: auto) { driver.reset() }
    }

    // MARK: Drawing

    static let paper: UInt32 = 0xEDEDEC
    static let ink: UInt32 = 0x17181C
    static let muted: UInt32 = 0x8C8C90

    static func money(_ cents: Int) -> String {
        "$\(cents / 100).\(String(format: "%02d", cents % 100))"
    }

    static func draw(_ c: inout GraphicsContext, _ g: G, _ f: OmeletteToppingBuilderFrame) {
        c.fill(Path(CGRect(x: 0, y: 0, width: g.w, height: g.h)), with: .color(A.color(paper)))
        header(&c, g)
        A.plate(&c, g.cx, g.plateY)
        plateContents(&c, g, f)
        shelf(&c, g, f)
        labels(&c, g, f)
        button(&c, g, f)
        for tp in f.toppings { flight(&c, g, f, tp) }
    }

    static func header(_ c: inout GraphicsContext, _ g: G) {
        let y = g.top + 28
        A.ringShadow(&c, 40, y + 2, 19, 25, 0.08)
        c.fill(A.circle(40, y, 20), with: .color(A.color(0xFFFFFF)))
        var chev = Path()
        chev.move(to: CGPoint(x: 43.5, y: y - 6.5))
        chev.addLine(to: CGPoint(x: 37, y: y))
        chev.addLine(to: CGPoint(x: 43.5, y: y + 6.5))
        c.stroke(chev, with: .color(A.color(ink)), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
        c.draw(Text("Omelette").font(.system(size: 20, weight: .semibold)).foregroundColor(A.color(ink)),
               at: CGPoint(x: g.cx, y: y), anchor: .center)
        c.draw(Text("Step 2 of 3").font(.system(size: 13)).foregroundColor(A.color(muted)),
               at: CGPoint(x: g.w - 24, y: y), anchor: .trailing)
        c.draw(Text("Tap a filling to fold it in").font(.system(size: 15)).foregroundColor(A.color(muted)),
               at: CGPoint(x: g.cx, y: g.top + 64), anchor: .center)
    }

    /// The omelette's squash: a damped dip on each impact.
    static func squash(_ f: OmeletteToppingBuilderFrame) -> Double {
        if f.reduced { return 0 }
        var k = 0.0
        for tp in f.toppings {
            let tau = f.t - tp.ta - M.land
            if tau > 0 && tau < 1.5 { k += 0.035 * exp(-7 * tau) * sin(20 * tau) }
        }
        return k
    }

    /// Rotates a vector by -a (a screen vector into a frame turned by a).
    static func unturn(_ x: Double, _ y: Double, _ a: Double) -> (Double, Double) {
        (x * cos(a) + y * sin(a), -x * sin(a) + y * cos(a))
    }

    static func plateContents(_ c: inout GraphicsContext, _ g: G, _ f: OmeletteToppingBuilderFrame) {
        let k = squash(f)
        var o = c
        o.translateBy(x: g.cx, y: g.plateY)
        o.scaleBy(x: 1 - k, y: 1 - k)
        o.rotate(by: .radians(M.angle))
        o.translateBy(x: 0, y: -M.centroidY)
        A.omelette(&o, shadowLocal: unturn(2, 5, M.angle), contactLocal: unturn(0, 1.5, M.angle))
        for tp in f.toppings where !f.reduced {
            // A soft puff where the ingredient lands.
            let u = (f.t - tp.ta - M.land) / 0.36
            if u < 0 || u >= 1 { continue }
            let L = M.landing(tp.kind), e = M.easeOut(u)
            o.stroke(A.circle(L.x, L.y, 10 + 46 * e), with: .color(A.color(0xFFF6D6, 0.55 * (1 - u))), lineWidth: 7 * (1 - e) + 0.5)
        }
        for tp in f.toppings { pieces(&o, f, tp) }
    }

    static func pieces(_ c: inout GraphicsContext, _ f: OmeletteToppingBuilderFrame, _ tp: OmeletteToppingBuilderTopping) {
        let k = tp.kind, t = f.t
        let L = M.landing(k)
        let tau = t - tp.ta - M.land
        if !f.reduced && tau < 0 { return }
        for p in M.pieces[k] {
            var alpha = 1.0, x = p.x, y = p.y, sc = 1.0, rot = p.rot, hop = 0.0
            if f.reduced {
                alpha = M.clamp01((t - tp.ta) / 0.3)
            } else {
                let dist = hypot(p.x - L.x, p.y - L.y)
                let dur = 0.28 + 0.26 * dist / M.R
                let u = M.clamp01((tau - p.delay) / dur)
                let e = M.easeOut(u)
                x = L.x + (p.x - L.x) * e
                y = L.y + (p.y - L.y) * e
                hop = sin(Double.pi * u) * (8 + 18 * dist / M.R)
                sc = 1 + hop / 60
                rot = p.rot + (1 - e) * p.spin
                let tl = tau - p.delay - dur
                if tl > 0 { sc *= 1 + 0.16 * exp(-8 * tl) * sin(-26 * tl) }
            }
            if let tr = tp.tr {
                let w = M.clamp01((t - tr - (f.reduced ? 0 : p.lift)) / (f.reduced ? 0.3 : M.liftDur))
                if w >= 1 { continue }
                if f.reduced {
                    alpha *= 1 - w
                } else {
                    let lift = M.easeIn(w)
                    alpha *= 1 - lift
                    sc *= 1 + 0.5 * lift
                    let up = unturn(0, -18 * lift, M.angle)
                    x += up.0
                    y += up.1
                    hop += 30 * lift
                }
            }
            if alpha <= 0.001 { continue }
            let s = sc * p.size * M.pieceScale[k]
            let turn = M.angle + rot
            let light = unturn(-0.55, -0.83, turn)
            let sh = unturn(1.0 + hop * 0.35, 1.6 + hop * 0.6, turn)
            var l = c
            l.opacity = alpha
            l.translateBy(x: x, y: y)
            l.rotate(by: .radians(rot))
            l.scaleBy(x: s, y: s)
            A.piece(&l, k, p, lx: light.0, ly: light.1, sx: sh.0 / s, sy: sh.1 / s, shadow: max(0, 1 - hop / 40))
        }
    }

    /// The omelette point (x, y) on the screen, without the squash.
    static func world(_ g: G, _ x: Double, _ y: Double) -> CGPoint {
        let a = M.angle, dy = y - M.centroidY
        return CGPoint(x: g.cx + x * cos(a) - dy * sin(a), y: g.plateY + x * sin(a) + dy * cos(a))
    }

    /// The latest topping of kind k that has been added by t.
    static func latest(_ f: OmeletteToppingBuilderFrame, _ k: Int) -> OmeletteToppingBuilderTopping? {
        f.toppings.last { $0.kind == k && $0.ta <= f.t }
    }

    static func shelf(_ c: inout GraphicsContext, _ g: G, _ f: OmeletteToppingBuilderFrame) {
        let y = g.shelfY
        A.ringShadow(&c, g.cx, y + 8, 44, 66, 0.07)
        A.ringShadow(&c, g.cx, y + 2, 47, 52, 0.10)
        c.fill(A.circle(g.cx, y, 48), with: A.radial([(0xFFFFFF, 1, 0), (0xF7F7F6, 1, 1)], g.cx - 14, y - 16, 64))
        for i in 0..<M.kinds {
            let d = M.slot(i, f.s)
            let ad = abs(d)
            if ad > 2.1 { continue }
            var x = g.cx + d * G.spacing
            let sc0 = 1 - 0.28 * min(1, ad)
            let alpha = 1 - M.smooth((ad - 1.2) / 0.8)
            var pop = 1.0
            var badge = 0.0
            if let tp = latest(f, i) {
                let tau = f.t - tp.ta
                if f.reduced {
                    badge = M.clamp01(tau / 0.3)
                } else {
                    pop = tau < M.restockAt ? 0 : M.easeOutBack((tau - M.restockAt) / M.restockDur)
                    badge = pop
                }
                if let tr = tp.tr { badge *= 1 - M.clamp01((f.t - tr) / 0.2) }
            }
            if f.shakeKind == i {
                let tau = f.t - f.shakeAt
                if tau >= 0 && tau < M.shakeDur { x += 7 * sin(tau * 42) * (1 - tau / M.shakeDur) }
            }
            let size = G.icon * sc0 * pop
            if size < 0.5 || alpha <= 0.001 { continue }
            var l = c
            l.opacity = alpha
            l.translateBy(x: x, y: y)
            l.scaleBy(x: size, y: size)
            A.ingredient(&l, i, ground: true)
            if badge > 0.01 {
                let bx = x + 27 * sc0, by = y - 26 * sc0, r = 10 * sc0 * badge
                var b = c
                b.opacity = alpha
                b.fill(A.circle(bx, by + 1, r + 2.5), with: .color(A.color(0x000000, 0.10)))
                b.fill(A.circle(bx, by, r + 2), with: .color(A.color(0xFFFFFF)))
                b.fill(A.circle(bx, by, r), with: .color(A.color(ink)))
                var tick = Path()
                tick.move(to: CGPoint(x: bx - r * 0.42, y: by + r * 0.02))
                tick.addLine(to: CGPoint(x: bx - r * 0.1, y: by + r * 0.34))
                tick.addLine(to: CGPoint(x: bx + r * 0.45, y: by - r * 0.32))
                b.stroke(tick, with: .color(A.color(0xFFFFFF)),
                         style: StrokeStyle(lineWidth: max(0.5, 2 * sc0 * badge), lineCap: .round, lineJoin: .round))
            }
        }
    }

    static func labels(_ c: inout GraphicsContext, _ g: G, _ f: OmeletteToppingBuilderFrame) {
        for i in 0..<M.kinds {
            let d = M.slot(i, f.s)
            let a = M.clamp01(1 - abs(d) * 1.8)
            if a <= 0.001 { continue }
            var l = c
            l.opacity = a
            let x = g.cx + 40 * d
            l.draw(Text(M.names[i]).font(.system(size: 17, weight: .semibold)).foregroundColor(A.color(ink)),
                   at: CGPoint(x: x, y: g.nameY), anchor: .center)
            l.draw(Text("+" + money(M.cents[i])).font(.system(size: 14)).foregroundColor(A.color(muted)),
                   at: CGPoint(x: x, y: g.priceY), anchor: .center)
        }
    }

    static func button(_ c: inout GraphicsContext, _ g: G, _ f: OmeletteToppingBuilderFrame) {
        let rect = CGRect(x: 20, y: g.buttonY, width: g.w - 40, height: 56)
        let shape = Path(roundedRect: rect, cornerRadius: 18)
        var sh = c
        sh.addFilter(.shadow(color: .black.opacity(0.18), radius: 10, x: 0, y: 6))
        sh.fill(shape, with: A.linear([(0x2A2B31, 1, 0), (0x17181C, 1, 1)], 0, rect.minY, 0, rect.maxY))
        var hl = c
        hl.clip(to: shape)
        hl.stroke(shape.offsetBy(dx: 0, dy: 1), with: .color(A.color(0xFFFFFF, 0.10)), lineWidth: 1.2)
        let my = rect.midY
        c.draw(Text("Continue").font(.system(size: 17, weight: .semibold)).foregroundColor(A.color(0xFFFFFF)),
               at: CGPoint(x: rect.minX + 24, y: my), anchor: .leading)

        let font = Font.system(size: 17, weight: .semibold).monospacedDigit()
        let now = M.total(f, at: f.t)
        var band = c
        band.clip(to: Path(CGRect(x: rect.midX, y: rect.minY + 14, width: rect.width / 2, height: rect.height - 28)))
        if let tc = M.lastChange(f, at: f.t), f.t - tc < M.priceRoll {
            let u = M.easeOut((f.t - tc) / M.priceRoll)
            let old = M.total(f, at: tc - 1e-6)
            let dir: Double = now >= old ? 1 : -1
            var a = band
            a.opacity = 1 - u
            a.draw(Text(money(old)).font(font).foregroundColor(A.color(0xFFFFFF)),
                   at: CGPoint(x: rect.maxX - 24, y: my - 22 * u * dir), anchor: .trailing)
            var b = band
            b.opacity = u
            b.draw(Text(money(now)).font(font).foregroundColor(A.color(0xFFFFFF)),
                   at: CGPoint(x: rect.maxX - 24, y: my + 22 * (1 - u) * dir), anchor: .trailing)
        } else {
            band.draw(Text(money(now)).font(font).foregroundColor(A.color(0xFFFFFF)),
                      at: CGPoint(x: rect.maxX - 24, y: my), anchor: .trailing)
        }
    }

    /// The ingredient tossed from the shelf onto the omelette, then collapsing into its pieces.
    static func flight(_ c: inout GraphicsContext, _ g: G, _ f: OmeletteToppingBuilderFrame, _ tp: OmeletteToppingBuilderTopping) {
        if f.reduced { return }
        let tau = f.t - tp.ta
        if tau < 0 || tau >= M.land + M.collapse { return }
        let sxy = CGPoint(x: g.cx + tp.sx * G.spacing, y: g.shelfY)
        let L = M.landing(tp.kind)
        let w = world(g, L.x, L.y)
        var x = sxy.x, y = sxy.y, sc = 1.0, rot = 0.0, alpha = 1.0, lift = 0.0
        if tau < M.press {
            sc = 1 - 0.1 * M.easeOut(tau / M.press)
        } else if tau < M.land {
            let e = M.easeInOut((tau - M.press) / (M.land - M.press))
            x = M.bezier(sxy.x, w.x + 50, w.x, e)
            y = M.bezier(sxy.y, w.y - 70, w.y, e)
            sc = 0.9 - 0.18 * e + 0.35 * sin(Double.pi * e)
            rot = 25 * Double.pi / 180 * e
            lift = sin(Double.pi * e)
        } else {
            let v = (tau - M.land) / M.collapse
            x = w.x
            y = w.y
            sc = 0.72 * (1 - M.easeIn(v))
            rot = 25 * Double.pi / 180
            alpha = 1 - v
        }
        A.blob(&c, x + 6 + 16 * lift, y + 34 * sc + 22 * lift, 28 * sc, 8 * sc, 0x000000, 0.16 * (1 - 0.5 * lift) * alpha)
        let size = G.icon * sc
        if size < 0.5 { return }
        var l = c
        l.opacity = alpha
        l.translateBy(x: x, y: y)
        l.rotate(by: .radians(rot))
        l.scaleBy(x: size, y: size)
        A.ingredient(&l, tp.kind, ground: false)
    }

    // MARK: Touch

    private func bump() {
        live = true
        token += 1
        let mine = token
        DispatchQueue.main.asyncAfter(deadline: .now() + 4.4) {
            if mine == token && !driver.touching { live = false }
        }
    }

    private func touch(_ g: G, auto: Bool, reduced: Bool) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                let now = Date().timeIntervalSinceReferenceDate
                if grab == nil {
                    let p = v.startLocation
                    if abs(p.y - g.shelfY) < 66 {
                        let sim = driver.session(at: now, auto: auto, reduced: reduced)
                        sim.beginDrag(at: now)
                        grab = .shelf(s0: sim.drag ?? 0)
                    } else if hypot(p.x - g.cx, p.y - g.plateY) < 150 {
                        _ = driver.session(at: now, auto: auto, reduced: reduced)
                        grab = .plate
                    } else {
                        grab = .ignored
                        return
                    }
                    driver.touching = true
                    driver.touched(at: now)
                    bump()
                }
                guard case .shelf(let s0) = grab, let sim = driver.live else { return }
                if abs(v.translation.width) > 8 {
                    sim.dragTo(s0 - v.translation.width / G.spacing)
                }
                driver.touched(at: now)
            }
            .onEnded { v in
                let now = Date().timeIntervalSinceReferenceDate
                defer { grab = nil }
                let moved = hypot(v.translation.width, v.translation.height)
                switch grab {
                case .shelf:
                    guard let sim = driver.live else { break }
                    if moved < 8 {
                        sim.release(at: now, velocity: 0)
                        let dx = v.startLocation.x - g.cx
                        if abs(dx) < 56 {
                            if sim.tapCentre(at: now) {
                                DispatchQueue.main.asyncAfter(deadline: .now() + (reduced ? 0 : M.land)) {
                                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                }
                            }
                        } else {
                            let d = max(-2, min(2, Int((dx / G.spacing).rounded())))
                            sim.select(d == 0 ? (dx > 0 ? 1 : -1) : d, at: now)
                        }
                    } else {
                        let vel = -v.velocity.width / G.spacing
                        sim.release(at: now, velocity: min(12, max(-12, vel)))
                    }
                case .plate:
                    if moved < 10, let sim = driver.live, sim.removeLast(at: now) {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    }
                default:
                    return
                }
                driver.touching = false
                driver.touched(at: now)
                bump()
            }
    }
}

struct OmeletteToppingBuilderGeo {
    var w = 393.0
    var h = 852.0
    var top = 59.0
    var bottom = 34.0

    static let spacing = 124.0
    static let icon = 80.0

    var cx: Double { w / 2 }
    var plateY: Double { top + 268 }
    var shelfY: Double { top + 536 }
    var nameY: Double { top + 614 }
    var priceY: Double { top + 637 }
    var buttonY: Double { h - bottom - 72 }
}
