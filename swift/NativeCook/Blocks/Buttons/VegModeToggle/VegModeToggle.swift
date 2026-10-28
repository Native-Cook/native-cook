import SwiftUI
import UIKit

/*
 Veg Mode Toggle — the diet switch of an invented food-delivery menu ("Tiffin Lane").

 Look: a warm white screen, "What's for dinner?", and a big pill toggle: a light grey track with a
 black knob covering two thirds of it. The free end holds the mode's dish, drawn in code, popping out
 of the toggle like a sticker: a glossy broccoli bunch for Veg, a drumstick and a halved egg for
 Non veg. The labels sit in a strip that slides the other way inside the knob, so the knob is a
 window that swaps them. Below, "24 veg dishes" rolls to "38 non-veg dishes" and the three dish rows
 swap with a stagger.

 Flip: the knob slides on a UI spring and stretches in travel, the old dish sinks below the track's
 bottom line, the new one rises from it with overshoot, a lean that settles and a squash; its egg or
 floret follows 60 ms late (VegModeToggleMotion.swift has the springs and the script).

 autoPlay: loop = 7 s (tap to Non veg, hold, drag back, release to Veg, rest).
 Touch: tap the toggle to flip; drag the knob to scrub between the two dishes, release to snap to
 the nearer end (with the finger's speed). A light haptic on each commit. 2 s after the last touch
 it springs back to Veg and the loop restarts.
 Reduced motion: the Veg state, still; a tap switches with short springs and the dishes fade.
*/
struct VegModeToggle: View {
    /// Plays the 7 s flip-and-drag loop until touched.
    var autoPlay = false

    typealias M = VegModeToggleMotion
    typealias A = VegModeToggleArt

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var driver = VegModeToggleDriver()
    @State private var live = false
    @State private var token = 0
    @State private var grab: Grab?

    private enum Grab {
        case ignored
        case knob(p0: Double, mode0: Int)
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
        let g = VegModeToggleGeo(w: max(1, size.width), h: max(1, size.height), top: insets.top)
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
        .accessibilityLabel("Veg mode")
        .accessibilityHint("Switches the menu between veg and non-veg dishes.")
        .accessibilityAddTraits(.isButton)
        .accessibilityAction {
            let now = Date().timeIntervalSinceReferenceDate
            driver.session(at: now, auto: auto, reduced: reduced).tap(at: now)
            bump()
        }
        .onChange(of: auto) { driver.reset() }
    }

    // MARK: Drawing

    static let paper: UInt32 = 0xF6F5F1
    static let ink: UInt32 = 0x171717
    static let muted: UInt32 = 0x8A877F
    static let rule: UInt32 = 0xE6E3DC

    static let dishes: [[(name: String, note: String, price: String)]] = [
        [("Paneer tikka bowl", "Charred paneer, mint rice", "$9.50"),
         ("Garden pulao", "Saffron rice, peas, cashew", "$8.00"),
         ("Masala corn cakes", "Crisp, with green chutney", "$6.50")],
        [("Smoky drumsticks", "Two pieces, tandoor spice", "$11.00"),
         ("Egg curry bowl", "Two eggs, onion gravy", "$9.00"),
         ("Chicken pulao", "Saffron rice, fried onion", "$10.50")],
    ]

    static func draw(_ c: inout GraphicsContext, _ g: VegModeToggleGeo, _ f: VegModeToggleFrame) {
        c.fill(Path(CGRect(x: 0, y: 0, width: g.w, height: g.h)), with: .color(A.color(paper)))
        header(&c, g)
        toggle(&c, g, f)
        list(&c, g, f)
    }

    static func header(_ c: inout GraphicsContext, _ g: VegModeToggleGeo) {
        c.draw(Text("Deliver to").font(.system(size: 13)).foregroundColor(A.color(muted)),
               at: CGPoint(x: 24, y: g.top + 18), anchor: .leading)
        c.draw(Text("Home · 2.1 km").font(.system(size: 16, weight: .semibold)).foregroundColor(A.color(ink)),
               at: CGPoint(x: 24, y: g.top + 38), anchor: .leading)

        let sx = g.w - 44, sy = g.top + 28
        c.stroke(A.circle(sx, sy, 20), with: .color(A.color(0xDEDBD4)), lineWidth: 1)
        c.stroke(A.circle(sx - 1.5, sy - 1.5, 6.5), with: .color(A.color(ink)), lineWidth: 1.8)
        var handle = Path()
        handle.move(to: CGPoint(x: sx + 3.3, y: sy + 3.3))
        handle.addLine(to: CGPoint(x: sx + 7, y: sy + 7))
        c.stroke(handle, with: .color(A.color(ink)), style: StrokeStyle(lineWidth: 1.8, lineCap: .round))

        c.draw(Text("TIFFIN LANE").font(.system(size: 12, weight: .semibold)).tracking(1.6)
                .foregroundColor(A.color(0xA09C93)),
               at: CGPoint(x: g.cx, y: g.top + 104), anchor: .center)
        c.draw(Text("What's for dinner?").font(.system(size: 30, weight: .bold)).kerning(-0.4)
                .foregroundColor(A.color(ink)),
               at: CGPoint(x: g.cx, y: g.top + 136), anchor: .center)
    }

    static func toggle(_ c: inout GraphicsContext, _ g: VegModeToggleGeo, _ f: VegModeToggleFrame) {
        let H = g.trackH, W = g.trackW, K = g.knobW
        var t = c
        t.translateBy(x: g.trackX, y: g.trackY)

        // Track, with a soft inner shadow along its top.
        let track = Path(roundedRect: CGRect(x: 0, y: 0, width: W, height: H), cornerRadius: H / 2)
        t.fill(track, with: .color(A.color(0xE7E5DF)))
        var inner = t
        inner.clip(to: track)
        inner.addFilter(.blur(radius: 4))
        inner.stroke(track.offsetBy(dx: 0, dy: 3), with: .color(.black.opacity(0.10)), lineWidth: 6)

        // Knob: slides by p, stretches with its speed, shrinks a little while pressed.
        let travel = W - K
        let kc = W - K / 2 - travel * f.p
        let kw = K * (1 + 0.10 * min(1, abs(f.pv) / 5))
        let ps = 1 - 0.03 * f.press
        let left = min(max(kc - kw / 2, -10), W - kw + 10)
        let rect = CGRect(x: left + kw * (1 - ps) / 2, y: H * (1 - ps) / 2, width: kw * ps, height: H * ps)
        let knob = Path(roundedRect: rect, cornerRadius: rect.height / 2)

        dishes(&t, g, f, front: false)

        var ks = t
        ks.addFilter(.shadow(color: .black.opacity(0.16), radius: 5, x: 0, y: 2))
        ks.fill(knob, with: A.linear([(0x2B2B2B, 0), (0x111111, 1)], 0, rect.minY, 0, rect.maxY))
        var hl = t
        hl.clip(to: knob)
        hl.stroke(knob.offsetBy(dx: 0, dy: 1.2),
                  with: .linearGradient(Gradient(colors: [.white.opacity(0.14), .white.opacity(0)]),
                                        startPoint: CGPoint(x: 0, y: rect.minY),
                                        endPoint: CGPoint(x: 0, y: rect.minY + H * 0.45)),
                  lineWidth: 1.5)

        // The labels ride a strip that moves against the knob: the knob is their window.
        var lc = t
        lc.clip(to: knob)
        // Each label fades as it leaves the middle, so a half-way knob is not two cut words.
        let label = Font.system(size: 25, weight: .medium)
        for (text, dx) in [("Veg", f.p * K), ("Non veg", -(1 - f.p) * K)] {
            let a = 1 - M.smooth(0.25, 0.6, abs(dx) / K)
            guard a > 0.001 else { continue }
            var l = lc
            l.opacity = a
            l.draw(Text(text).font(label).foregroundColor(A.color(0xFAFAF7)),
                   at: CGPoint(x: kc + dx, y: H / 2), anchor: .center)
        }

        dishes(&t, g, f, front: true)
    }

    /// The dishes on one side of the knob, cut by the track's bottom line, with their soft shadow.
    static func dishes(_ c: inout GraphicsContext, _ g: VegModeToggleGeo, _ f: VegModeToggleFrame, front: Bool) {
        let H = g.trackH
        var clip = c
        clip.clip(to: Path(CGRect(x: -400, y: -400, width: 1200, height: 400 + H)))
        let items: [(on: Bool, a: Double, v: Double, a2: Double, veg: Bool)] = [
            (f.bFront == front, f.b, f.bv, f.b2, true),
            (f.mFront == front, f.m, f.mv, f.m2, false),
        ]
        for d in items where d.on && d.a > 0.002 {
            let alpha = f.reduced ? M.clamp01(d.a) : 1
            func paint(_ l: inout GraphicsContext, _ silhouette: Bool) {
                l.scaleBy(x: H, y: H)
                if d.veg {
                    A.broccoli(&l, a: d.a, v: d.v, a2: d.a2, reduced: f.reduced, silhouette: silhouette)
                } else {
                    A.drumstick(&l, a: d.a, v: d.v, a2: d.a2, reduced: f.reduced, silhouette: silhouette)
                }
            }
            var sh = clip
            sh.opacity = 0.10 * alpha
            sh.addFilter(.blur(radius: 6))
            sh.drawLayer { l in
                l.translateBy(x: 0, y: 4)
                paint(&l, true)
            }
            var art = clip
            art.opacity = alpha
            if alpha < 0.999 {
                art.drawLayer { l in paint(&l, false) }
            } else {
                paint(&art, false)
            }
        }
    }

    /// How far row i has swapped to Non veg (the top row leads both ways).
    static func rowSwap(_ f: VegModeToggleFrame, _ i: Int) -> Double {
        let d = 0.12 * Double(i)
        return f.qTarget >= 0.5 ? M.smooth((f.q - d) / 0.64) : 1 - M.smooth(((1 - f.q) - d) / 0.64)
    }

    static func mark(_ c: inout GraphicsContext, _ x: Double, _ y: Double, veg: Bool) {
        let tone = A.color(veg ? 0x2E9E47 : 0xB5442C)
        c.stroke(Path(roundedRect: CGRect(x: x - 8, y: y - 8, width: 16, height: 16), cornerRadius: 3),
                 with: .color(tone), lineWidth: 1.5)
        if veg {
            c.fill(A.circle(x, y, 3.6), with: .color(tone))
        } else {
            var tri = Path()
            tri.move(to: CGPoint(x: x, y: y - 4.3))
            tri.addLine(to: CGPoint(x: x + 4, y: y + 2.6))
            tri.addLine(to: CGPoint(x: x - 4, y: y + 2.6))
            tri.closeSubpath()
            c.fill(tri, with: .color(tone))
        }
    }

    static func list(_ c: inout GraphicsContext, _ g: VegModeToggleGeo, _ f: VegModeToggleFrame) {
        // "24 veg dishes": the count rolls like an odometer, the words swap with row 0.
        let y = g.headerY
        let numFont = Font.system(size: 17, weight: .semibold).monospacedDigit()
        let numW = c.resolve(Text("38").font(numFont)).measure(in: CGSize(width: 200, height: 40)).width
        let v = 24 + 14 * M.clamp01(f.q)
        let n = v.rounded(.down)
        let fr = v - n
        var band = c
        band.clip(to: Path(CGRect(x: 0, y: y - 13, width: g.w, height: 26)))
        var d0 = band
        d0.opacity = 1 - fr
        d0.draw(Text("\(Int(n))").font(numFont).foregroundColor(A.color(ink)),
                at: CGPoint(x: 24, y: y - 20 * fr), anchor: .leading)
        if fr > 0.001 {
            var d1 = band
            d1.opacity = fr
            d1.draw(Text("\(Int(n) + 1)").font(numFont).foregroundColor(A.color(ink)),
                    at: CGPoint(x: 24, y: y + 20 * (1 - fr)), anchor: .leading)
        }
        let l0 = rowSwap(f, 0)
        let words = Font.system(size: 17, weight: .semibold)
        for (k, text) in ["veg dishes", "non-veg dishes"].enumerated() {
            let a = k == 0 ? 1 - l0 : l0
            guard a > 0.001 else { continue }
            var w = band
            w.opacity = a
            w.draw(Text(text).font(words).foregroundColor(A.color(ink)),
                   at: CGPoint(x: 24 + numW + 5, y: y + (k == 0 ? -10 * l0 : 10 * (1 - l0))), anchor: .leading)
        }
        c.draw(Text("Sort · Popular").font(.system(size: 14)).foregroundColor(A.color(muted)),
               at: CGPoint(x: g.w - 24, y: y), anchor: .trailing)
        c.fill(Path(CGRect(x: 24, y: y + 26, width: g.w - 48, height: 1)), with: .color(A.color(rule)))

        for i in 0..<3 {
            let top = g.rowsY + 76 * Double(i)
            let l = rowSwap(f, i)
            var row = c
            row.clip(to: Path(CGRect(x: 0, y: top + 4, width: g.w, height: 68)))
            for k in 0..<2 {
                let a = k == 0 ? 1 - l : l
                guard a > 0.001 else { continue }
                let dish = dishes[k][i]
                var r = row
                r.opacity = a
                r.translateBy(x: 0, y: k == 0 ? -10 * l : 10 * (1 - l))
                mark(&r, 32, top + 24, veg: k == 0)
                r.draw(Text(dish.name).font(.system(size: 17, weight: .semibold)).foregroundColor(A.color(ink)),
                       at: CGPoint(x: 52, y: top + 24), anchor: .leading)
                r.draw(Text(dish.note).font(.system(size: 14)).foregroundColor(A.color(muted)),
                       at: CGPoint(x: 52, y: top + 47), anchor: .leading)
                r.draw(Text(dish.price).font(.system(size: 16, weight: .semibold)).foregroundColor(A.color(ink)),
                       at: CGPoint(x: g.w - 24, y: top + 24), anchor: .trailing)
            }
            c.fill(Path(CGRect(x: 24, y: top + 75, width: g.w - 48, height: 1)), with: .color(A.color(rule)))
        }
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

    /// Past either end the knob gives a little, then holds.
    static func rubber(_ raw: Double) -> Double {
        if raw < 0 { let o = -raw; return -0.5 * o / (1 + o / 0.25) }
        if raw > 1 { let o = raw - 1; return 1 + 0.5 * o / (1 + o / 0.25) }
        return raw
    }

    private func touch(_ g: VegModeToggleGeo, auto: Bool, reduced: Bool) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                let now = Date().timeIntervalSinceReferenceDate
                if grab == nil {
                    let hit = CGRect(x: g.trackX - 24, y: g.trackY - 36, width: g.trackW + 48, height: g.trackH + 60)
                    guard hit.contains(v.startLocation) else { grab = .ignored; return }
                    let sim = driver.session(at: now, auto: auto, reduced: reduced)
                    driver.touching = true
                    sim.beginDrag(at: now)
                    grab = .knob(p0: sim.drag?.p ?? Double(sim.mode), mode0: sim.mode)
                    bump()
                }
                guard case .knob(let p0, _) = grab, let sim = driver.live else { return }
                sim.dragTo(Self.rubber(p0 - v.translation.width / (g.trackW - g.knobW)))
                driver.touched(at: now)
            }
            .onEnded { v in
                let now = Date().timeIntervalSinceReferenceDate
                defer { grab = nil }
                guard case .knob(_, let mode0) = grab, let sim = driver.live else { return }
                if hypot(v.translation.width, v.translation.height) < 8 {
                    sim.release(at: now, velocity: 0, target: 1 - mode0, delay: M.enterDelay)
                } else {
                    let pv = -v.velocity.width / (g.trackW - g.knobW)
                    sim.release(at: now, velocity: min(12, max(-12, pv)))
                }
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                driver.touching = false
                driver.touched(at: now)
                bump()
            }
    }
}

struct VegModeToggleGeo {
    var w = 393.0
    var h = 852.0
    var top = 59.0

    let trackW = 252.0
    let trackH = 104.0
    let knobW = 168.0

    var cx: Double { w / 2 }
    var trackX: Double { (w - trackW) / 2 }
    var trackY: Double { top + 248 }
    var headerY: Double { top + 440 }
    var rowsY: Double { top + 470 }
}
