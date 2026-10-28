import SwiftUI
import UIKit

/*
 Guest Count Figures — the guests step of a stay-booking flow.

 Look: a warm white screen, "Guests — Add up to 4 people, including yourself.", a row of little
 painted people standing on the floor (drawn in code, GuestCountFiguresArt.swift), a big rolling
 numeral between − and +, an "Add children" pill, a four-step progress bar and Back / Next.
 Each guest added hops in beside the others with a squash on landing while the group re-centres
 on a spring and greets the newcomer with a small hop; a guest removed crouches, rises and fades.
 "Add children" folds the big counter into two compact rows (Adults, Children); "Adults only"
 folds it back.

 autoPlay: loop = 10 s (GuestCountFiguresMotion.swift has the script).
 Touch: tap − / +, tap the pill, or drag sideways on the big numeral (or on a row) to scrub the
 count, one step every 32 pt. At most 4 people, at least 1 adult. A light haptic on each change.
 2 s after the last touch the screen eases back to one adult and the loop restarts.
 Reduced motion: a still of 2 adults and 2 children in rows; touches still change the counts,
 without motion.
*/
struct GuestCountFigures: View {
    /// Plays the 10 s add-and-remove loop until touched.
    var autoPlay = false

    typealias M = GuestCountFiguresMotion
    typealias A = GuestCountFiguresArt

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var sim = GuestCountFiguresSim()
    @State private var live = false
    @State private var token = 0
    @State private var grab: Grab?

    private struct Grab {
        enum Kind { case button(GuestCountFiguresControl), scrubAdults, scrubChildren, pill, none }
        var kind: Kind
        var start: GuestCountFiguresState
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
        .background(A.color(0xFCFBF9).ignoresSafeArea())
        .preferredColorScheme(.light)
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let g = GuestCountFiguresGeo(w: max(1, size.width), h: max(1, size.height),
                                     top: insets.top, bottom: insets.bottom)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let paused = !auto && !live
        return TimelineView(.animation(paused: paused)) { timeline in
            let now = timeline.date.timeIntervalSinceReferenceDate
            let f = sim.frame(at: now, cx: g.cx, auto: auto, reduced: reduced)
            Canvas { c, _ in
                Self.draw(&c, g, f)
            }
            .frame(width: size.width, height: size.height)
        }
        .contentShape(Rectangle())
        .gesture(touch(g, auto: auto, reduced: reduced))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Guests. Add up to 4 people, including yourself.")
        .onChange(of: auto) { sim.reset() }
    }

    // MARK: Drawing

    static let ink: UInt32 = 0x222222
    static let muted: UInt32 = 0x717171
    static let line: UInt32 = 0xDDDDDD

    static func mix(_ a: UInt32, _ b: UInt32, _ t: Double) -> Color {
        let x = A.rgb(a), y = A.rgb(b), k = min(1, max(0, t))
        return Color(.sRGB, red: x.0 + (y.0 - x.0) * k, green: x.1 + (y.1 - x.1) * k, blue: x.2 + (y.2 - x.2) * k)
    }
    static func lerp(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

    static func draw(_ c: inout GraphicsContext, _ g: GuestCountFiguresGeo, _ f: GuestCountFiguresFrame) {
        c.fill(Path(CGRect(x: 0, y: 0, width: g.w, height: g.h)), with: .color(A.color(0xFCFBF9)))
        header(&c, g)
        figures(&c, g, f)
        counter(&c, g, f)
        footer(&c, g)
    }

    static func header(_ c: inout GraphicsContext, _ g: GuestCountFiguresGeo) {
        let exit = c.resolve(Text("Exit").font(.system(size: 14, weight: .medium)).foregroundColor(A.color(ink)))
        let ew = exit.measure(in: CGSize(width: 200, height: 40)).width + 28
        let pill = Path(roundedRect: CGRect(x: 20, y: g.top + 6, width: ew, height: 32), cornerRadius: 16)
        c.stroke(pill, with: .color(A.color(line)), lineWidth: 1)
        c.draw(exit, at: CGPoint(x: 20 + ew / 2, y: g.top + 22), anchor: .center)

        c.draw(Text("Guests").font(.system(size: 32, weight: .semibold)).kerning(-0.4).foregroundColor(A.color(ink)),
               at: CGPoint(x: g.cx, y: g.top + 78), anchor: .center)
        c.draw(Text("Add up to 4 people, including yourself.").font(.system(size: 16)).foregroundColor(A.color(0x6A6A6A)),
               at: CGPoint(x: g.cx, y: g.top + 114), anchor: .center)
    }

    static func figures(_ c: inout GraphicsContext, _ g: GuestCountFiguresGeo, _ f: GuestCountFiguresFrame) {
        for fig in f.figures {
            var s = c
            s.translateBy(x: fig.x, y: g.ground)
            let k = fig.sx * (1 - min(fig.lift / 80, 0.5))
            A.drawShadow(&s, slot: M.slot(fig.index), k: k, alpha: fig.alpha * (1 - min(fig.lift / 40, 0.8)))
        }
        for fig in f.figures {
            let look = A.looks[fig.index]
            var fc = c
            fc.translateBy(x: fig.x, y: g.ground - fig.lift)
            fc.scaleBy(x: fig.sx * look.scale * M.figureScale, y: fig.sy * look.scale * M.figureScale)
            if fig.alpha < 0.999 {
                // Fade the figure as one layer, so its overlapping parts don't show through.
                fc.opacity = fig.alpha
                fc.drawLayer { l in A.drawFigure(&l, look) }
            } else {
                A.drawFigure(&fc, look)
            }
        }
    }

    /// A digit wheel: the value's neighbours roll past the centre with their alpha and size falling off.
    static func wheel(_ c: inout GraphicsContext, _ v: Double, at p: CGPoint, scale: Double, alpha: Double) {
        guard alpha > 0.001 else { return }
        var band = c
        band.clip(to: Path(CGRect(x: p.x - 80 * scale, y: p.y - 64 * scale, width: 160 * scale, height: 128 * scale)))
        let base = Int(v.rounded(.down))
        for k in (base - 1)...(base + 2) where k >= 0 && k <= 9 {
            let o = Double(k) - v
            let a = max(0, 1 - 1.5 * abs(o)) * alpha
            guard a > 0.001 else { continue }
            var d = band
            d.opacity = a
            d.translateBy(x: p.x, y: p.y + o * 72 * scale)
            let s = scale * (1 - 0.25 * min(1, abs(o)))
            d.scaleBy(x: s, y: s)
            d.draw(Text("\(k)").font(.system(size: 100, weight: .bold)).foregroundColor(A.color(ink)),
                   at: .zero, anchor: .center)
        }
    }

    static func button(_ c: inout GraphicsContext, _ p: CGPoint, d: Double, plus: Bool, disabled: Double,
                       press: Double, alpha: Double) {
        guard alpha > 0.001 else { return }
        var b = c
        b.opacity = alpha
        b.translateBy(x: p.x, y: p.y)
        let s = 1 - 0.12 * press
        b.scaleBy(x: s, y: s)
        let circle = Path(ellipseIn: CGRect(x: -d / 2, y: -d / 2, width: d, height: d))
        if press > 0.001 { b.fill(circle, with: .color(A.color(0xF2F2F2, press))) }
        b.stroke(circle, with: .color(mix(line, 0xEEEEEE, disabled)), lineWidth: 1)
        let l = d * 0.18
        var glyph = Path()
        glyph.move(to: CGPoint(x: -l, y: 0))
        glyph.addLine(to: CGPoint(x: l, y: 0))
        if plus {
            glyph.move(to: CGPoint(x: 0, y: -l))
            glyph.addLine(to: CGPoint(x: 0, y: l))
        }
        b.stroke(glyph, with: .color(mix(ink, 0xD2D2D2, disabled)),
                 style: StrokeStyle(lineWidth: d > 40 ? 2 : 1.6, lineCap: .round))
    }

    static func counter(_ c: inout GraphicsContext, _ g: GuestCountFiguresGeo, _ f: GuestCountFiguresFrame) {
        let m = f.m
        let mc = min(1, max(0, m))

        // Adults: the big counter folding into its row.
        let num = CGPoint(x: lerp(g.cx, g.rowNumX, m), y: lerp(g.numY, g.rowA, m))
        wheel(&c, f.adultsV, at: num, scale: lerp(1, 0.17, m), alpha: 1)
        let bd = lerp(44, 36, mc)
        button(&c, CGPoint(x: lerp(g.cx - 120, g.rowMinusX, m), y: lerp(g.numY, g.rowA, m)), d: bd, plus: false,
               disabled: f.disabled[0], press: f.press[0], alpha: 1)
        button(&c, CGPoint(x: lerp(g.cx + 120, g.rowPlusX, m), y: lerp(g.numY, g.rowA, m)), d: bd, plus: true,
               disabled: f.disabled[1], press: f.press[1], alpha: 1)

        let adults = c.resolve(Text("Adults").font(.system(size: 17, weight: .medium)).foregroundColor(A.color(ink)))
        let aged = c.resolve(Text("Aged 13 or above").font(.system(size: 14)).foregroundColor(A.color(muted)))
        let aw = adults.measure(in: CGSize(width: 300, height: 40)).width
        let sw = aged.measure(in: CGSize(width: 300, height: 40)).width
        c.draw(adults, at: CGPoint(x: lerp(g.cx - aw / 2, 24, m), y: lerp(g.top + 468, g.top + 374, m)), anchor: .leading)
        c.draw(aged, at: CGPoint(x: lerp(g.cx - sw / 2, 24, m), y: lerp(g.top + 490, g.top + 396, m)), anchor: .leading)

        // Divider and the children row.
        let dp = M.smooth((m - 0.3) / 0.5)
        if dp > 0.001 {
            var p = Path()
            p.move(to: CGPoint(x: 24, y: g.divider))
            p.addLine(to: CGPoint(x: 24 + (g.w - 48) * dp, y: g.divider))
            c.stroke(p, with: .color(A.color(0xEBEBEB)), lineWidth: 1)
        }
        let ca = M.smooth((m - 0.35) / 0.65)
        if ca > 0.001 {
            var r = c
            r.opacity = ca
            r.translateBy(x: 0, y: 16 * (1 - ca))
            r.draw(Text("Children").font(.system(size: 17, weight: .medium)).foregroundColor(A.color(ink)),
                   at: CGPoint(x: 24, y: g.top + 452), anchor: .leading)
            r.draw(Text("Ages 2–12").font(.system(size: 14)).foregroundColor(A.color(muted)),
                   at: CGPoint(x: 24, y: g.top + 474), anchor: .leading)
            wheel(&r, f.childrenV, at: CGPoint(x: g.rowNumX, y: g.rowC), scale: 0.17, alpha: 1)
            button(&r, CGPoint(x: g.rowMinusX, y: g.rowC), d: 36, plus: false, disabled: f.disabled[2], press: f.press[2], alpha: 1)
            button(&r, CGPoint(x: g.rowPlusX, y: g.rowC), d: 36, plus: true, disabled: f.disabled[3], press: f.press[3], alpha: 1)
        }

        // The pill: "Add children" ↔ "Adults only".
        let add = c.resolve(Text("Add children").font(.system(size: 15, weight: .medium)).foregroundColor(A.color(ink)))
        let only = c.resolve(Text("Adults only").font(.system(size: 15, weight: .medium)).foregroundColor(A.color(ink)))
        let w0 = add.measure(in: CGSize(width: 300, height: 40)).width
        let w1 = only.measure(in: CGSize(width: 300, height: 40)).width
        let pw = lerp(w0, w1, mc) + 36
        var pc = c
        pc.translateBy(x: g.cx, y: g.pillY)
        let ps = 1 - 0.08 * f.press[4]
        pc.scaleBy(x: ps, y: ps)
        let pill = Path(roundedRect: CGRect(x: -pw / 2, y: -20, width: pw, height: 40), cornerRadius: 20)
        if f.press[4] > 0.001 { pc.fill(pill, with: .color(A.color(0xF2F2F2, f.press[4]))) }
        pc.stroke(pill, with: .color(A.color(line)), lineWidth: 1)
        let xf = M.smooth((m - 0.3) / 0.4)
        // The labels swap with a short roll: the old one leaves 8 pt up, the new one comes from below.
        var t0 = pc
        t0.opacity = 1 - xf
        t0.draw(add, at: CGPoint(x: 0, y: -8 * xf), anchor: .center)
        var t1 = pc
        t1.opacity = xf
        t1.draw(only, at: CGPoint(x: 0, y: 8 * (1 - xf)), anchor: .center)
    }

    static func footer(_ c: inout GraphicsContext, _ g: GuestCountFiguresGeo) {
        let segW = (g.w - 12) / 4
        for i in 0..<4 {
            c.fill(Path(CGRect(x: Double(i) * (segW + 4), y: g.progressY - 2, width: segW, height: 4)),
                   with: .color(A.color(i < 2 ? ink : 0xE3E3E3)))
        }
        c.draw(Text("Back").font(.system(size: 16, weight: .semibold)).underline().foregroundColor(A.color(ink)),
               at: CGPoint(x: 24, y: g.footY), anchor: .leading)
        let next = CGRect(x: g.w - 24 - 112, y: g.footY - 24, width: 112, height: 48)
        c.fill(Path(roundedRect: next, cornerRadius: 10), with: .color(A.color(ink)))
        c.draw(Text("Next").font(.system(size: 16, weight: .semibold)).foregroundColor(.white),
               at: CGPoint(x: next.midX, y: next.midY), anchor: .center)
    }

    // MARK: Touch

    private func bump(_ reduced: Bool) {
        live = true
        token += 1
        let mine = token
        DispatchQueue.main.asyncAfter(deadline: .now() + 3.2) {
            if mine == token && !sim.touching && !(autoPlay && !reduceMotion) { live = false }
        }
    }

    private func feel() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    private static func near(_ p: CGPoint, _ x: Double, _ y: Double, _ r: Double) -> Bool {
        hypot(p.x - x, p.y - y) <= r
    }

    private func hit(_ p: CGPoint, _ s: GuestCountFiguresState, _ g: GuestCountFiguresGeo) -> Grab.Kind {
        if abs(p.x - g.cx) < 90 && abs(p.y - g.pillY) < 26 { return .pill }
        if s.rows {
            if Self.near(p, g.rowMinusX, g.rowA, 26) { return .button(.adultMinus) }
            if Self.near(p, g.rowPlusX, g.rowA, 26) { return .button(.adultPlus) }
            if Self.near(p, g.rowMinusX, g.rowC, 26) { return .button(.childMinus) }
            if Self.near(p, g.rowPlusX, g.rowC, 26) { return .button(.childPlus) }
            if abs(p.y - g.rowA) < 36 { return .scrubAdults }
            if abs(p.y - g.rowC) < 36 { return .scrubChildren }
        } else {
            if Self.near(p, g.cx - 120, g.numY, 30) { return .button(.adultMinus) }
            if Self.near(p, g.cx + 120, g.numY, 30) { return .button(.adultPlus) }
            if abs(p.x - g.cx) < 96 && abs(p.y - g.numY) < 70 { return .scrubAdults }
        }
        return .none
    }

    static func clampAdults(_ n: Int, _ s: GuestCountFiguresState) -> Int { min(max(n, 1), M.maxPeople - s.children) }
    static func clampChildren(_ n: Int, _ s: GuestCountFiguresState) -> Int { min(max(n, 0), M.maxPeople - s.adults) }

    private func touch(_ g: GuestCountFiguresGeo, auto: Bool, reduced: Bool) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                let now = Date().timeIntervalSinceReferenceDate
                if grab == nil {
                    let s = sim.state(at: now, auto: auto, reduced: reduced)
                    grab = Grab(kind: hit(v.startLocation, s, g), start: s)
                    sim.touching = true
                    sim.touch(now, auto: auto, reduced: reduced)
                    bump(reduced)
                }
                guard let gr = grab else { return }
                let steps = Int((v.translation.width / 32).rounded(.towardZero))
                var s = sim.state(at: now, auto: auto, reduced: reduced)
                switch gr.kind {
                case .scrubAdults:
                    s.adults = Self.clampAdults(gr.start.adults + steps, s)
                    if sim.apply(s, press: .none, at: now, auto: auto, reduced: reduced) { feel(); bump(reduced) }
                case .scrubChildren:
                    s.children = Self.clampChildren(gr.start.children + steps, s)
                    if sim.apply(s, press: .none, at: now, auto: auto, reduced: reduced) { feel(); bump(reduced) }
                default:
                    sim.touch(now, auto: auto, reduced: reduced)
                }
            }
            .onEnded { v in
                let now = Date().timeIntervalSinceReferenceDate
                defer {
                    grab = nil
                    sim.touching = false
                    sim.touch(now, auto: auto, reduced: reduced)
                    bump(reduced)
                }
                guard let gr = grab, hypot(v.translation.width, v.translation.height) < 14 else { return }
                var s = sim.state(at: now, auto: auto, reduced: reduced)
                var press = GuestCountFiguresControl.none
                switch gr.kind {
                case .button(let b):
                    press = b
                    switch b {
                    case .adultMinus: s.adults = Self.clampAdults(s.adults - 1, s)
                    case .adultPlus: s.adults = Self.clampAdults(s.adults + 1, s)
                    case .childMinus: s.children = Self.clampChildren(s.children - 1, s)
                    case .childPlus: s.children = Self.clampChildren(s.children + 1, s)
                    default: break
                    }
                case .pill:
                    press = .pill
                    if s.rows { s.rows = false; s.children = 0 } else { s.rows = true }
                default:
                    return
                }
                if sim.apply(s, press: press, at: now, auto: auto, reduced: reduced) { feel() }
            }
    }
}
