import SwiftUI
import UIKit

/*
 Folded Map Card — "Ridge Line Trail" in a hiking app: the offline map is a paper map folded in
 thirds. It unfolds in 3D (each panel turning on its crease, shaded by its angle), then the route
 draws itself on, the summit pin drops and the elevation profile rises. The loop and the fold
 geometry are in FoldedMapCardMotion.swift (loop = 8 s).

 Each panel is a plane, so it is drawn as its pre-rendered image under an exact perspective
 homography (`projectionEffect`) rather than the React Native version's six textured strips; its
 shading gradient rides on the image in the panel's own coordinates.

 Touch: drag sideways on the map to fold or unfold it continuously (two panel widths of drag =
 fully open, rubber-banded past both ends); released, panel 1 springs to the nearer state with
 the finger's velocity (ω 11, ζ 0.72, a slight overshoot) and panel 2 follows on a softer spring
 (ω 22, ζ 0.8), which staggers them. A tap folds or unfolds it. Once open, the route draws in 1 s,
 the pin drops at 0.8 s, the profile rises from 0.2 s; folding takes them away first. Light haptic
 when the map lands open or shut. 2 s after the last touch it refolds in 0.8 s and the loop
 restarts. Reduced motion: no loop; the map unfolds without overshoot; everything else fades.
*/

struct FoldedMapCard: View {
    /// Plays the 8 s demo: unfold, draw the route, drop the pin, refold.
    var autoPlay = false

    static let loop = FoldedMapCardMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = FoldedMapCardEngine()
    /// True while a touch, its spring or the return to rest still moves things outside autoPlay.
    @State private var live = false
    /// The printed map, built off the main thread on first appearance.
    @State private var loaded: FoldedMapCardArt?

    typealias M = FoldedMapCardMotion
    typealias S = FoldedMapCardStyle

    var body: some View {
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
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let l = FoldedMapCardLayout(size: size, insets: insets)
        let scale = min(3, displayScale)
        let art = loaded ?? FoldedMapCardArt.cached(mw: l.mw, mh: l.mh, scale: scale)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        engine.onRest = { live = false }

        return ZStack(alignment: .topLeading) {
            Rectangle()
                .colorEffect(ShaderLibrary.foldedMapCardBackdrop(.float2(max(1, size.width), max(1, size.height))))
                .frame(width: size.width, height: size.height)

            TimelineView(.animation(paused: !(auto || live))) { timeline in
                if let art {
                    let s = engine.step(to: timeline.date, auto: auto, reduced: reduced)
                    scene(s, layout: l, art: art, reduced: reduced)
                }
            }
            .task(id: FoldedMapCardArt.key(mw: l.mw, mh: l.mh, scale: scale)) {
                guard art == nil else { return }
                let built = await FoldedMapCardArt.load(mw: l.mw, mh: l.mh, pw: l.pw, scale: scale)
                // The loop starts with the first frame that shows the map.
                engine.restart()
                loaded = built
            }

            header(l)

            Color.clear
                .contentShape(Rectangle())
                .frame(width: l.mw, height: l.mh)
                .offset(x: l.card.x, y: l.card.y)
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { v in
                            live = true
                            engine.dragChanged(v.translation, velocity: v.velocity, pw: l.pw)
                        }
                        .onEnded { v in engine.dragEnded(v.translation) }
                )
                .accessibilityElement()
                .accessibilityAddTraits(.isButton)
                .accessibilityLabel("Folded paper map of the trail. Tap or drag sideways to unfold it.")

            Text("Start route")
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(Color(hex: 0xFFF4EC))
                .frame(width: l.mw, height: 56)
                .background(
                    Capsule()
                        .fill(S.accent)
                        .overlay(
                            Capsule().strokeBorder(
                                LinearGradient(colors: [Color.rgba(255, 220, 200, 0.45), .clear], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.12)),
                                lineWidth: 1
                            )
                        )
                        .shadow(color: Color.rgba(224, 100, 63, 0.28), radius: 14, y: 12)
                        .shadow(color: Color.rgba(60, 20, 8, 0.4), radius: 1, y: 1)
                )
                .offset(x: S.pad, y: size.height - insets.bottom - 16 - 56)
                .allowsHitTesting(false)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .background(S.screen)
        .clipped()
    }

    // MARK: Per frame

    @ViewBuilder
    private func scene(_ s: FoldedMapCardEngine.State, layout l: FoldedMapCardLayout, art: FoldedMapCardArt, reduced: Bool) -> some View {
        let mesh = M.mesh(l.card, u1: s.u1, u2: reduced ? s.u1 : s.u2)
        ZStack(alignment: .topLeading) {
            under(mesh.frames, s, l, art, reduced)
            ForEach(0..<3, id: \.self) { k in
                panel(mesh.panels[k], k: k, l, art)
            }
            over(mesh.frames, s, l, art, reduced)

            // The note about the sheet, beside the folded map.
            VStack(alignment: .leading, spacing: 4) {
                Text("PAPER MAP")
                    .font(.system(size: 11, weight: .bold))
                    .kerning(1.4)
                    .foregroundStyle(S.sage)
                Text("Sheet 7 · 1:25 000")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(S.ink)
                Text("Waterproof print, contours every 20 m, huts and water sources marked.")
                    .font(.system(size: 13))
                    .lineSpacing(2)
                    .foregroundStyle(S.inkSoft)
                    .fixedSize(horizontal: false, vertical: true)
                Text("Drag to unfold  ›")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(S.accent)
                    .padding(.top, 8)
            }
            .frame(width: max(0, l.size.width - S.pad - (S.pad + l.pw + 22)), alignment: .leading)
            .offset(x: S.pad + l.pw + 22, y: l.card.y + 12)
            .opacity(1 - M.clamp(s.u1 * 2.4, 0, 1))
            .allowsHitTesting(false)

            // The profile card's labels.
            let labels = M.clamp(s.rise * 3, 0, 1)
            HStack {
                Text("Elevation")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(S.ink)
                Spacer(minLength: 8)
                Text("1 394 m → 2 214 m")
                    .font(.system(size: 13, weight: .medium).monospacedDigit())
                    .foregroundStyle(S.inkSoft)
                    .opacity(labels)
            }
            .frame(width: l.mw - 32)
            .offset(x: S.pad + 16, y: l.profY + 12)
            .allowsHitTesting(false)

            HStack {
                Text("0 km")
                Spacer(minLength: 4)
                Text("Larch Hut · 6.1 km")
                Spacer(minLength: 4)
                Text("14.2 km")
            }
            .font(.system(size: 11, weight: .medium).monospacedDigit())
            .foregroundStyle(S.inkSoft)
            .frame(width: l.mw - 32)
            .offset(x: S.pad + 16, y: l.profY + 30 + l.profileH + 4)
            .opacity(labels)
            .allowsHitTesting(false)
        }
        .frame(width: l.size.width, height: l.size.height, alignment: .topLeading)
    }

    /// One panel: its image under the panel's perspective, its shading laid over it.
    @ViewBuilder
    private func panel(_ p: FoldedMapCardMotion.Panel, k: Int, _ l: FoldedMapCardLayout, _ art: FoldedMapCardArt) -> some View {
        let h = FoldedMapCardHomography.make(width: l.pw, height: l.mh, quad: p.quad)
        let stops = zip([0, 0.12, 0.88, 1.0], p.tints).map { pos, c in
            Gradient.Stop(color: Color.rgba(c.r, c.g, c.b, c.a), location: pos)
        }
        (p.front ? art.fronts[k] : art.back)
            .resizable()
            .interpolation(.high)
            .frame(width: l.pw, height: l.mh)
            .overlay(LinearGradient(stops: stops, startPoint: .leading, endPoint: .trailing))
            .projectionEffect(h ?? ProjectionTransform())
            .opacity(h == nil ? 0 : 1)
            .allowsHitTesting(false)
    }

    /// The profile card and its bars, then the map's shadows on the table.
    private func under(_ f: [FoldedMapCardMotion.Frame], _ s: FoldedMapCardEngine.State, _ l: FoldedMapCardLayout, _ art: FoldedMapCardArt, _ reduced: Bool) -> some View {
        Canvas { ctx, _ in
            let card = l.card
            ctx.fill(
                Path(roundedRect: CGRect(x: S.pad, y: l.profY, width: l.mw, height: l.profileH + 50), cornerRadius: 20, style: .continuous),
                with: .color(Color.rgba(232, 240, 226, 0.05))
            )

            // Elevation profile: bars of the route's height rising left to right.
            if s.rise > 0 {
                var bars = Path()
                let n = FoldedMapCardArt.profileCount
                let bw = (l.mw - 32) / Double(n)
                for i in 0..<n {
                    let k = reduced ? 1 : M.easeOut(M.clamp(s.rise * 1.6 - Double(i) / Double(n) * 0.6, 0, 1))
                    let h = (8 + art.profile[i] * (l.profileH - 8)) * k
                    if h < 0.5 { continue }
                    bars.addRoundedRect(
                        in: CGRect(x: S.pad + 16 + Double(i) * bw + 1, y: l.profY + 30 + l.profileH - h, width: bw - 2.2, height: h),
                        cornerSize: CGSize(width: 1.5, height: 1.5)
                    )
                }
                var layer = ctx
                layer.opacity = reduced ? s.rise : 1
                layer.fill(bars, with: .linearGradient(
                    Gradient(stops: [
                        .init(color: Color(hex: 0xF08A5D), location: 0),
                        .init(color: S.accent, location: 0.5),
                        .init(color: Color(hex: 0x9C3B26), location: 1),
                    ]),
                    startPoint: CGPoint(x: 0, y: l.profY + 30),
                    endPoint: CGPoint(x: 0, y: l.profY + 30 + l.profileH)
                ))
            }

            // The map's shadow on the table: each panel's footprint, pushed out by its height.
            var shadow = Path()
            var maxX = card.pw
            for k in 0..<3 {
                let fr = f[k]
                let x1 = fr.ox + card.pw * cos(fr.psi)
                let z1 = fr.oz - card.pw * sin(fr.psi)
                let a = min(fr.ox, x1)
                let b = max(fr.ox, x1)
                let lift = max(-fr.oz, -z1)
                shadow.addRect(CGRect(x: card.x + a + 3 + lift * 0.08, y: card.y + 6 + lift * 0.12, width: max(2, b - a), height: card.h))
                maxX = max(maxX, x1)
            }
            ctx.drawLayer { c in
                c.addFilter(.blur(radius: 12))
                c.fill(shadow, with: .color(Color.rgba(2, 10, 6, 0.5)))
            }
            ctx.drawLayer { c in
                c.addFilter(.blur(radius: 2.5))
                c.fill(
                    Path(roundedRect: CGRect(x: card.x, y: card.y + 1.5, width: maxX, height: card.h), cornerRadius: 1),
                    with: .color(Color.rgba(2, 10, 6, 0.45))
                )
            }
        }
        .frame(width: l.size.width, height: l.size.height)
        .allowsHitTesting(false)
    }

    /// The route through the folds, drawn up to its progress, and the summit pin.
    private func over(_ f: [FoldedMapCardMotion.Frame], _ s: FoldedMapCardEngine.State, _ l: FoldedMapCardLayout, _ art: FoldedMapCardArt, _ reduced: Bool) -> some View {
        Canvas { ctx, _ in
            let card = l.card
            if s.route > 0.001 {
                var route = Path()
                let n = art.route.count
                let upto = Int(floor(s.route * Double(n - 1)))
                for i in 0...upto {
                    let p = M.project(card, f, mx: art.route[i].x, my: art.route[i].y)
                    if i == 0 { route.move(to: p) } else { route.addLine(to: p) }
                }
                var layer = ctx
                layer.opacity = M.clamp(s.route * 6, 0, 1)
                layer.stroke(route, with: .color(Color.rgba(250, 244, 228, 0.9)), style: StrokeStyle(lineWidth: 4.5, lineCap: .round, lineJoin: .round))
                layer.stroke(route, with: .color(S.accent), style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
            }

            let pinOpacity = reduced ? s.pinP : M.clamp(s.pinP * 5, 0, 1)
            if pinOpacity > 0, let summit = art.route.last {
                let tip = CGPoint(x: card.x + summit.x, y: card.y + summit.y)
                let d = M.pinDrop(s.pinP)
                var layer = ctx
                layer.opacity = pinOpacity
                layer.drawLayer { c in
                    c.opacity = 1 - max(0, d) / 30
                    c.addFilter(.blur(radius: 1.5))
                    c.fill(
                        Path(roundedRect: CGRect(x: tip.x - 6, y: tip.y - 2, width: 12, height: 4), cornerRadius: 2),
                        with: .color(Color.rgba(40, 20, 10, 0.35))
                    )
                }
                let sc = d < 0 ? 0.6 : 1
                let at = CGPoint(x: tip.x, y: tip.y - max(0, d))
                layer.draw(FoldedMapCardArt.pin, in: CGRect(x: at.x - 20 * sc, y: at.y - 40 * sc, width: 40 * sc, height: 46 * sc))
            }
        }
        .frame(width: l.size.width, height: l.size.height)
        .allowsHitTesting(false)
    }

    // MARK: Static

    private func header(_ l: FoldedMapCardLayout) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("SAVED OFFLINE · ALDER VALLEY")
                .font(.system(size: 11, weight: .bold))
                .kerning(1.6)
                .foregroundStyle(S.sage)
            Text("Ridge Line Trail")
                .font(.system(size: 32, weight: .bold))
                .kerning(-0.5)
                .foregroundStyle(S.ink)
            HStack(alignment: .top, spacing: 24) {
                stat("14.2 km", "Distance")
                stat("820 m", "Ascent")
                stat("5 h 10 min", "Moving time")
            }
            .padding(.top, 10)
        }
        .frame(width: l.mw, alignment: .leading)
        .offset(x: S.pad, y: l.top)
        .allowsHitTesting(false)
    }

    private func stat(_ value: String, _ label: String) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(value)
                .font(.system(size: 17, weight: .semibold).monospacedDigit())
                .foregroundStyle(S.ink)
            Text(label)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(S.inkSoft)
        }
    }
}

enum FoldedMapCardStyle {
    static let pad = 20.0
    static let accent = Color(hex: 0xE0643F)
    static let ink = Color(hex: 0xEEF1E8)
    static let inkSoft = Color.rgba(226, 234, 222, 0.62)
    static let sage = Color(hex: 0xA9C79A)
    static let screen = Color(hex: 0x13221A)
}

struct FoldedMapCardLayout {
    let size: CGSize
    let top: Double
    let mw: Double
    let pw: Double
    let mh: Double
    let profY: Double
    let profileH: Double
    let card: FoldedMapCardMotion.Card

    init(size: CGSize, insets: EdgeInsets) {
        typealias M = FoldedMapCardMotion
        self.size = size
        top = insets.top + 16
        mw = size.width - FoldedMapCardStyle.pad * 2
        pw = mw / 3
        mh = M.clamp(size.height * 0.36, 196, 300).rounded()
        let mapY = top + 132
        profY = mapY + mh + 28
        // The profile card takes the room down to the button.
        let ctaTop = size.height - insets.bottom - 16 - 56
        profileH = M.clamp(ctaTop - 20 - profY - 50, 56, 120).rounded()
        card = M.Card(x: FoldedMapCardStyle.pad, y: mapY, w: mw, h: mh, pw: pw)
    }
}

/// The projective transform that takes a width × height rectangle onto a quad (top left, top
/// right, bottom right, bottom left). Nil when the quad has collapsed to a line (a panel seen
/// exactly edge-on).
enum FoldedMapCardHomography {
    static func make(width: Double, height: Double, quad q: [CGPoint]) -> ProjectionTransform? {
        let x0 = q[0].x, y0 = q[0].y
        let x1 = q[1].x, y1 = q[1].y
        let x2 = q[2].x, y2 = q[2].y
        let x3 = q[3].x, y3 = q[3].y
        // Shoelace area of the quad.
        let area = 0.5 * abs((x0 * y1 - x1 * y0) + (x1 * y2 - x2 * y1) + (x2 * y3 - x3 * y2) + (x3 * y0 - x0 * y3))
        if area < 0.5 { return nil }
        let dx1 = x1 - x2, dx2 = x3 - x2, dx3 = x0 - x1 + x2 - x3
        let dy1 = y1 - y2, dy2 = y3 - y2, dy3 = y0 - y1 + y2 - y3
        var a, b, c, d, e, f, g, h: Double
        if abs(dx3) < 1e-9 && abs(dy3) < 1e-9 {
            a = x1 - x0; b = x3 - x0; c = x0
            d = y1 - y0; e = y3 - y0; f = y0
            g = 0; h = 0
        } else {
            let den = dx1 * dy2 - dx2 * dy1
            if abs(den) < 1e-9 { return nil }
            g = (dx3 * dy2 - dx2 * dy3) / den
            h = (dx1 * dy3 - dx3 * dy1) / den
            a = x1 - x0 + g * x1; b = x3 - x0 + h * x3; c = x0
            d = y1 - y0 + g * y1; e = y3 - y0 + h * y3; f = y0
        }
        // From the unit square to the rectangle's own size.
        a /= width; d /= width; g /= width
        b /= height; e /= height; h /= height
        return ProjectionTransform(CATransform3D(
            m11: a, m12: d, m13: 0, m14: g,
            m21: b, m22: e, m23: 0, m24: h,
            m31: 0, m32: 0, m33: 1, m34: 0,
            m41: c, m42: f, m43: 0, m44: 1
        ))
    }
}

/// The fold's state, stepped once a frame like the React Native frame callback.
@MainActor
final class FoldedMapCardEngine {
    typealias M = FoldedMapCardMotion

    struct State {
        var u1 = 0.0
        var u2 = 0.0
        var route = 0.0
        var pinP = 0.0
        var rise = 0.0
    }

    /// 0 loop, 1 dragging, 2 released, 3 returning.
    private var mode = 0
    private var s = State()
    private var u1V = 0.0
    private var u2V = 0.0
    private var goal = 0.0
    private var shown = 0.0
    private var idle = 0.0
    private var ret = 0.0
    private var fromU = 0.0
    private var startU = 0.0
    private var loopStart: Date?
    private var last: Date?
    private var wasAuto: Bool?
    private var reduced = false
    // The finger, between its landing and lifting.
    private var touching = false
    private var panning = false
    private var tapDead = false
    private let haptic = UIImpactFeedbackGenerator(style: .light)
    var onRest: () -> Void = {}

    func step(to date: Date, auto: Bool, reduced: Bool) -> State {
        self.reduced = reduced
        if wasAuto != auto {
            wasAuto = auto
            loopStart = date
            if mode != 1 { mode = 0 }
        }
        let raw = last.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        last = date
        if loopStart == nil { loopStart = date }
        // A stalled frame pauses the loop instead of skipping part of it.
        if raw > 0.25, let start = loopStart { loopStart = start.addingTimeInterval(raw - 1.0 / 60) }
        let dt = min(max(raw, 0), 1.0 / 20)

        if mode == 0 {
            if !auto {
                s = State()
                return s
            }
            let t = date.timeIntervalSince(loopStart ?? date).truncatingRemainder(dividingBy: M.loop)
            let p = M.script(max(0, t))
            s = State(u1: p.u1, u2: p.u2, route: p.route, pinP: p.pin, rise: p.rise)
            return s
        }
        let before = s.u1
        if mode == 2 {
            let w = 11.0
            let z = reduced ? 1 : 0.72
            let a = -w * w * (s.u1 - goal) - 2 * z * w * u1V
            u1V += a * dt
            s.u1 += u1V * dt
            if abs(s.u1 - goal) < 0.004 && abs(u1V) < 0.02 { idle += dt }
            if (goal == 1 && before < 0.99 && s.u1 >= 0.99) || (goal == 0 && before > 0.01 && s.u1 <= 0.01) {
                haptic.impactOccurred()
            }
            if idle > M.idleBeforeReturn {
                mode = 3
                ret = 0
                fromU = s.u1
            }
        } else if mode == 3 {
            let r = min(1, ret + dt / M.returnFor)
            ret = r
            s.u1 = fromU * (1 - M.easeInOut(r))
            if r >= 1 {
                mode = 0
                loopStart = date
                s = State()
                shown = 0
                u1V = 0
                u2V = 0
                if !auto { DispatchQueue.main.async { self.onRest() } }
                return s
            }
        }
        // Panel 2 trails panel 1.
        let w2 = 22.0
        let a2 = -w2 * w2 * (s.u2 - s.u1) - 2 * 0.8 * w2 * u2V
        u2V += a2 * dt
        s.u2 += u2V * dt
        if mode == 3 { s.u2 += (s.u1 - s.u2) * ret }
        // Route, pin and profile while the map lies open; they leave four times faster.
        let open = s.u1 > 0.96 && s.u2 > 0.96 && mode != 3
        shown = open ? shown + dt : max(0, shown - dt * 4)
        s.route = M.easeInOut(shown / 1.0)
        s.pinP = M.clamp((shown - 0.8) / 0.4, 0, 1)
        s.rise = M.clamp((shown - 0.2) / 0.8, 0, 1)
        return s
    }

    /// Starts the loop again from t = 0 at the next frame.
    func restart() {
        loopStart = nil
        last = nil
    }

    private func takeOver() {
        if mode == 0 {
            u1V = 0
            u2V = 0
            shown = s.route > 0.99 ? 1.2 : s.route
        }
        idle = 0
    }

    private func rubber(_ v: Double) -> Double {
        if v < 0 { return v / (1 - v * 4) * 0.4 }
        if v > 1 { return 1 + (v - 1) / (1 + (v - 1) * 4) * 0.4 }
        return v
    }

    func dragChanged(_ translation: CGSize, velocity: CGSize, pw: Double) {
        if !touching {
            touching = true
            panning = false
            tapDead = false
            haptic.prepare()
        }
        if !panning {
            if abs(translation.width) > 8 {
                panning = true
                takeOver()
                mode = 1
                startU = s.u1
            } else if hypot(translation.width, translation.height) > 10 {
                tapDead = true
            }
        }
        if panning {
            s.u1 = rubber(startU + translation.width / (2 * pw))
            u1V = velocity.width / (2 * pw)
        }
    }

    func dragEnded(_ translation: CGSize) {
        defer { touching = false }
        if panning {
            panning = false
            goal = s.u1 + u1V * 0.25 > 0.5 ? 1 : 0
            mode = 2
            idle = 0
        } else if !tapDead && hypot(translation.width, translation.height) <= 10 {
            takeOver()
            goal = s.u1 > 0.5 ? 0 : 1
            mode = 2
        }
    }
}
