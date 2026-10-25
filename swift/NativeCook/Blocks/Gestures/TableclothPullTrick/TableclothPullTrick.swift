import SwiftUI
import UIKit

/*
 Tablecloth Pull Trick — a dinner-party planner ("Supper Club — Table for six") whose hero is the
 set table seen from above the front: a checked linen cloth (a textured mesh) under six place
 settings, wine glasses and two candles. Grab the hem and yank: fast enough (> 1300 pt/s at the
 release) and the cloth whips off in 0.55 s, a wave running through it toward you, ghosts and speed
 streaks behind it, while each dish hops as the cloth leaves from under it and jiggles; too slow
 and the dishes slide along with it (up to 40 pt) and the glasses lean. A clean pull rolls the
 counter up and shows a badge. The cloth is then thrown back over the table and billows down.

 Layers: a Canvas with the baked room and walnut table; the cloth mesh in a Metal view (baked linen
 texture plus a shade per vertex: SwiftUI has no textured-triangle API); a Canvas with the speed
 streaks, candlelight pooled on the cloth and the dishes back to front; a second Metal view for
 the cloth while it is thrown back over the dishes; a Canvas with the counter and the badge. Both
 Metal views draw inside the SwiftUI transaction of the frame, so nothing lags.

 Touch takes over at once. 2 s after the last touch the cloth is thrown back on (2.4 s, as in the
 loop) or, if it is still on the table, eases back (1 s), the dishes go home, and the loop
 restarts. Reduced motion: no loop; a pull jumps to its result.
 */
struct TableclothPullTrick: View {
    /// Yanks the cloth (a clean pull), then throws it back on, on a 9 s loop.
    var autoPlay = false

    static let loop = 9.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = TableclothPullTrickEngine()
    @State private var art: TableclothPullTrickImages?
    @State private var awake = true

    private typealias P = TableclothPullTrickPaint
    private static let guests: [(String, UInt32)] = [
        ("A", 0xC0603A), ("B", 0x4F7A3A), ("C", 0x7A5BA6), ("D", 0xB8873A), ("E", 0x3F7F8C), ("F", 0xA8475E),
    ]

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            let g = TableclothPullTrickGeo(w: size.width, h: size.height, top: insets.top, bottom: insets.bottom)
            stage(g)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
                .task(id: g) { await bake(g) }
        }
        .background(P.color(0x1E1513).ignoresSafeArea())
    }

    private func bake(_ g: TableclothPullTrickGeo) async {
        guard g.w > 0, g.h > 0, art?.geo != g else { return }
        let scale = min(displayScale, 3)
        let made = await Task.detached(priority: .userInitiated) { () -> TableclothPullTrickImages? in
            guard let linen = TableclothPullTrickScene.linen() else { return nil }
            return TableclothPullTrickImages(
                geo: g,
                room: TableclothPullTrickScene.room(g, scale: scale),
                linen: linen,
                sprites: TableclothPullTrickSprites.make(scale: scale)
            )
        }.value
        if let made { art = made }
    }

    @ViewBuilder
    private func stage(_ g: TableclothPullTrickGeo) -> some View {
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        ZStack(alignment: .topLeading) {
            P.color(0x1E1513)
            if let art, art.geo == g {
                TimelineView(.animation(paused: !auto && !awake)) { timeline in
                    let p = engine.step(timeline.date, auto: auto, reduced: reduced, g: g)
                    ZStack(alignment: .topLeading) {
                        Canvas { ctx, _ in
                            ctx.draw(Image(uiImage: art.room), in: CGRect(x: 0, y: 0, width: g.w, height: g.h))
                        }
                        TableclothPullTrickMeshView(frame: engine.under, linen: art.linen, number: engine.under.number)
                        Canvas { ctx, _ in
                            TableclothPullTrickScene.streaks(ctx, g, p)
                            TableclothPullTrickDishes.candlePools(ctx, g, time: p.time)
                            TableclothPullTrickDishes.draw(ctx, g, art.sprites, p)
                        }
                        TableclothPullTrickMeshView(frame: engine.over, linen: art.linen, number: engine.over.number)
                        Canvas { ctx, _ in
                            hud(ctx, g, p)
                        }
                    }
                    .frame(width: g.w, height: g.h)
                    .onChange(of: engine.settled, initial: true) { _, settled in
                        if settled && !auto { awake = false }
                    }
                }
                .frame(width: g.w, height: g.h)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { v in
                            awake = true
                            engine.drag(v, g: g)
                        }
                        .onEnded { v in
                            awake = true
                            engine.release(v, reduced: reduced)
                        }
                )
                .accessibilityElement()
                .accessibilityLabel("A table set for six. Grab the tablecloth's hem and yank it fast to pull it out from under the dishes.")
                .accessibilityAddTraits(.allowsDirectInteraction)

                header(g)
                panel(g)
            }
        }
        .frame(width: g.w, height: g.h, alignment: .topLeading)
        .clipped()
    }

    // MARK: HUD

    private static let countFont = Font.system(size: 32, weight: .bold)
    private static let countUI = P.sans(32, .bold)
    private static let labelFont = Font.system(size: 10, weight: .bold)
    private static let badgeFont = Font.system(size: 13, weight: .semibold)
    private static let badgeUI = P.sans(13, .semibold)

    private static func text(_ ctx: GraphicsContext, _ s: String, _ font: Font, _ color: Color, x: CGFloat, baseline: CGFloat, kern: CGFloat = 0) {
        let r = ctx.resolve(Text(s).font(font).kerning(kern).foregroundColor(color))
        let b = r.firstBaseline(in: CGSize(width: 10_000, height: 10_000))
        ctx.draw(r, at: CGPoint(x: x, y: baseline - b), anchor: .topLeading)
    }

    /// The clean-pull counter (an odometer) in the header and the "Clean pull" badge over the table.
    private func hud(_ ctx: GraphicsContext, _ g: TableclothPullTrickGeo, _ p: TableclothPullTrickPose) {
        let right = g.w - 24
        let top = g.top
        let label = "CLEAN PULLS"
        let lw = P.width(label, P.sans(10, .bold), kern: 1.4) - 1.4
        Self.text(ctx, label, Self.labelFont, P.color(0xF4E7D4, 0.5), x: right - lw, baseline: top + 28, kern: 1.4)
        let v = max(0, p.count)
        let f = floor(v)
        let x = TableclothPullTrickMotion.clamp((v - f - 0.35) / 0.3, 0, 1)
        let r = f + x * x * (3 - 2 * x)
        let i = Int(floor(r))
        let fr = r - Double(i)
        let lineH: CGFloat = 34
        let base = top + 64
        var clip = ctx
        clip.clip(to: Path(CGRect(x: right - 80, y: base - lineH + 4, width: 80, height: lineH + 2)))
        for j in 0..<2 {
            let a = 1 - min(1, abs(Double(j) - fr))
            if a <= 0.01 { continue }
            let s = "\(i + j)"
            Self.text(clip, s, Self.countFont, P.color(0xFFF1DC, a), x: right - P.width(s, Self.countUI), baseline: base + CGFloat(Double(j) - fr) * lineH)
        }
        // Badge: drops in over the far edge of the table.
        guard p.badge > 0.01 else { return }
        let back = g.point(0, TableclothPullTrickMotion.depth, 0)
        let text = "Clean pull · dishes stayed"
        let w = P.width(text, Self.badgeUI) + 40
        let y = back.y - 46 - CGFloat(1 - p.badge) * 10
        let left = g.cx - w / 2
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 10))
            l.fill(Path(roundedRect: CGRect(x: left, y: y + 6, width: w, height: 30), cornerRadius: 15), with: .color(P.color(0x000000, 0.35 * p.badge)))
        }
        ctx.fill(Path(roundedRect: CGRect(x: left, y: y, width: w, height: 30), cornerRadius: 15), with: .color(P.color(0xF3E2C4, p.badge)))
        ctx.fill(Path(ellipseIn: CGRect(x: left + 17 - 6.5, y: y + 15 - 6.5, width: 13, height: 13)), with: .color(P.color(0x4F7A3A, p.badge)))
        var tick = Path()
        tick.move(to: CGPoint(x: left + 14, y: y + 15))
        tick.addLine(to: CGPoint(x: left + 16.3, y: y + 17.4))
        tick.addLine(to: CGPoint(x: left + 20.2, y: y + 12.8))
        ctx.stroke(tick, with: .color(P.color(0xF3E2C4, p.badge)), style: StrokeStyle(lineWidth: 1.6))
        Self.text(ctx, text, Self.badgeFont, P.color(0x3A2412, p.badge), x: left + 30, baseline: y + 19.5)
    }

    // MARK: Views

    private func header(_ g: TableclothPullTrickGeo) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("SUPPER CLUB")
                .font(.system(size: 11, weight: .bold))
                .kerning(1.8)
                .foregroundStyle(P.color(0xF4E7D4, 0.5))
            Text("Table for six")
                .font(.system(size: 30, weight: .bold))
                .kerning(-0.4)
                .foregroundStyle(P.color(0xFBF0DF))
            Text("Sat 19 Oct · 7:30 pm · 14 Hollis Lane")
                .font(.system(size: 14))
                .foregroundStyle(P.color(0xF4E7D4, 0.6))
        }
        .frame(width: g.w - 144, alignment: .leading)
        .offset(x: 24, y: g.top + 16)
        .allowsHitTesting(false)
    }

    private func panel(_ g: TableclothPullTrickGeo) -> some View {
        VStack(spacing: 16) {
            HStack(spacing: 12) {
                HStack(spacing: -8) {
                    ForEach(0..<Self.guests.count, id: \.self) { i in
                        let guest = Self.guests[i]
                        Text(guest.0)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(P.color(0xFFF4E4))
                            .frame(width: 32, height: 32)
                            .background(Circle().fill(P.color(guest.1)))
                            .overlay(Circle().strokeBorder(P.color(0x211714), lineWidth: 2))
                    }
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text("6 of 6 coming")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(P.color(0xFBF0DF))
                    Text("Yank the hem — fast — before dessert")
                        .font(.system(size: 13))
                        .foregroundStyle(P.color(0xF4E7D4, 0.55))
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, 8)
            Button {} label: {
                Text("Send the menu")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(P.color(0x3A2412))
                    .frame(maxWidth: .infinity, minHeight: 56, maxHeight: 56)
                    .background(Capsule().fill(P.color(0xE9B872)))
                    .shadow(color: P.color(0x000000, 0.3), radius: 1, y: 1)
                    .shadow(color: P.color(0xE9B872, 0.18), radius: 11, y: 8)
                    .contentShape(Capsule())
            }
            .buttonStyle(TableclothPullTrickPress())
        }
        .padding(.top, 20)
        .padding(.horizontal, 16)
        .padding(.bottom, max(g.bottom, 12))
        .frame(width: g.w, height: g.h - g.panelTop, alignment: .top)
        .background(
            UnevenRoundedRectangle(topLeadingRadius: 28, topTrailingRadius: 28, style: .continuous)
                .fill(P.color(0x211714))
                .shadow(color: P.color(0x000000, 0.45), radius: 16, y: -16)
                .overlay(alignment: .top) {
                    UnevenRoundedRectangle(topLeadingRadius: 28, topTrailingRadius: 28, style: .continuous)
                        .stroke(P.color(0xFFECD2, 0.08), lineWidth: 1)
                        .offset(y: -1)
                        .mask(alignment: .top) { Rectangle().frame(height: 28) }
                }
        )
        .offset(y: g.panelTop)
    }
}

private struct TableclothPullTrickPress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.8), value: configuration.isPressed)
    }
}

struct TableclothPullTrickImages: @unchecked Sendable {
    let geo: TableclothPullTrickGeo
    let room: UIImage
    let linen: CGImage
    let sprites: TableclothPullTrickSprites
}

/// The clock, the finger's pull and the dishes' drift, stepped once a frame. Also builds the
/// meshes the two Metal views draw.
final class TableclothPullTrickEngine {
    private typealias M = TableclothPullTrickMotion

    private static let idle = 2.0
    private static let reset = 1.0
    private static let fast = 1300.0
    private static let maxSlide = 40.0

    /// The finger's state: pull, its velocity, the dishes' drift, glass lean spring, events.
    private struct User {
        var d = 0.0
        var v = 0.0
        var slide = 0.0
        var tip = 0.0
        var tipV = 0.0
        var wave = 0.0
        var fling = 0.0
        var whipAt = -1.0
        var whipFrom = 0.0
        var jig = 0.0
        var count = 3.0
        var shown = 3.0
        var cleanAt = -1.0
    }

    /// 0 loop / rest, 1 a finger is in charge, 2 the cloth going back on.
    private var mode = 0
    private var loopT = -1.0
    private var last: Double?
    private var wasAuto: Bool?
    private var lastTouch = 0.0
    private var pulling = false
    private var touchStarted = false
    private var grab: Double?
    private var user = User()
    private var back = (at: 0.0, d: 0.0, slide: 0.0, count: 3.0, thrown: false)
    private var pose = M.restPose(0, 3)
    private(set) var settled = false
    let under = TableclothPullTrickMeshFrame()
    let over = TableclothPullTrickMeshFrame()
    private var buffers: [[Float]] = [[], [], []]
    private var overBuffer: [Float] = []
    private let grabFeel = UIImpactFeedbackGenerator(style: .light)
    private let messyFeel = UIImpactFeedbackGenerator(style: .medium)
    private let cleanFeel = UINotificationFeedbackGenerator()

    private var now: Double { Date().timeIntervalSinceReferenceDate }

    func step(_ date: Date, auto: Bool, reduced: Bool, g: TableclothPullTrickGeo) -> TableclothPullTrickPose {
        let t1 = date.timeIntervalSinceReferenceDate
        let raw = last.map { t1 - $0 } ?? 0
        let fresh = last == nil || raw > 0
        last = t1
        if fresh {
            advance(t1, raw: raw, auto: auto, reduced: reduced)
            build(g)
        }
        return pose
    }

    private func advance(_ t1: Double, raw: Double, auto: Bool, reduced: Bool) {
        let dt = min(max(raw, 0), 0.05)
        if wasAuto != auto {
            wasAuto = auto
            mode = 0
            loopT = auto ? 0 : -1
        }
        settled = false
        let sway = (t1.truncatingRemainder(dividingBy: M.loop) + M.loop).truncatingRemainder(dividingBy: M.loop)
        if mode == 0 && loopT >= 0 {
            // A frame that stalls pauses the loop instead of skipping ahead.
            if raw > 0 && raw <= 0.25 { loopT = (loopT + raw).truncatingRemainder(dividingBy: M.loop) }
            pose = M.autoPose(loopT)
            return
        }
        if mode == 2 {
            // Back to rest: thrown back on if the cloth is gone, else eased back; the dishes go home.
            let r = t1 - back.at
            var p = M.restPose(sway, 3)
            let done: Bool
            if back.thrown {
                let tb = M.throwBack(r)
                p.lift = tb.lift
                p.back = tb.back
                p.air = tb.air
                p.hang = tb.hang
                p.wave = tb.wave
                done = r >= M.throwBackTime
            } else {
                p.d = back.d * (1 - M.easeOut(M.seg(r, 0, Self.reset)))
                done = r >= Self.reset
            }
            let e = M.easeInOut(M.seg(r, 0.2, back.thrown ? 2.0 : Self.reset))
            p.slide = back.slide * (1 - e)
            p.count = back.count + (3 - back.count) * e
            pose = p
            if done {
                user = User()
                mode = 0
                loopT = 0
            }
            return
        }
        var s = user
        var speed = 0.0
        var yankAge = -1.0
        if s.whipAt >= 0 {
            // The yank, as in the loop: u^1.6 over 0.55 s, a wave and a whip down the drape.
            let age = t1 - s.whipAt
            let y = M.yankPull(s.whipFrom, age)
            s.d = y.0
            speed = y.1
            s.fling = 0.85 * sin(.pi * M.clamp((age - 0.1) / 0.75, 0, 1))
            s.wave = 26 * sin(.pi * M.clamp((age - 0.03) / 0.67, 0, 1))
            yankAge = age
            if age >= M.yank + 1.7 { s.whipAt = -1 }
        } else if !pulling && s.d < M.out - 1 && dt > 0 {
            // Let go slowly: the cloth springs back under the dishes (ω 9, ζ 0.75).
            let steps = max(1, Int(ceil(dt * 240)))
            let h = dt / Double(steps)
            for _ in 0..<steps {
                let acc = -81 * s.d - 13.5 * s.v
                s.v += acc * h
                s.d += s.v * h
            }
            s.wave *= exp(-dt * 4)
            s.fling *= exp(-dt * 6)
        }
        // Glass lean: a spring back to upright (ω 14, ζ 0.18 — glasses wobble).
        if dt > 0 {
            let steps = max(1, Int(ceil(dt * 240)))
            let h = dt / Double(steps)
            for _ in 0..<steps {
                let acc = -196 * s.tip - 5 * s.tipV
                s.tipV += acc * h
                s.tip += s.tipV * h
            }
        }
        s.shown += (s.count - s.shown) * (1 - exp(-dt * 9))
        user = s
        let badge = s.cleanAt >= 0 ? M.easeOut(M.seg(t1, s.cleanAt, s.cleanAt + 0.3)) * (1 - M.seg(t1, s.cleanAt + 2.2, s.cleanAt + 2.6)) : 0
        var p = M.restPose(sway, s.shown)
        p.d = s.d
        p.speed = speed
        p.wave = s.wave
        p.fling = s.fling
        p.slide = s.slide
        p.yankAge = yankAge
        p.yankFrom = s.whipFrom
        p.jig = s.jig
        p.tip = s.tip
        p.badge = badge
        pose = p
        let idle = !pulling && s.whipAt < 0 && t1 - lastTouch > Self.idle
        if mode == 1 && auto && idle {
            mode = 2
            back = (t1, s.d, s.slide, s.shown, s.d >= M.out - 1)
        }
        // Without autoPlay the screen rests once the cloth, the glasses and the counter are still.
        settled = !auto && idle && abs(s.v) < 0.05 && (abs(s.d) < 0.05 || s.d >= M.out - 1) && abs(s.tip) < 0.02
            && abs(s.tipV) < 0.05 && abs(s.count - s.shown) < 0.002 && badge == 0 && s.wave < 0.05 && s.fling < 0.005
    }

    /// The meshes for this frame: ghosts and cloth under the dishes, or the cloth over them.
    private func build(_ g: TableclothPullTrickGeo) {
        let p = pose
        var layers: [(vertices: [Float], alpha: Float)] = []
        if p.speed > 300 {
            var g1 = p
            g1.d = p.d - p.speed * 0.06
            var g2 = p
            g2.d = p.d - p.speed * 0.03
            if TableclothPullTrickScene.vertices(g, g1, alpha: 0.16, into: &buffers[0]) { layers.append((buffers[0], 0.16)) }
            if TableclothPullTrickScene.vertices(g, g2, alpha: 0.3, into: &buffers[1]) { layers.append((buffers[1], 0.3)) }
        }
        if TableclothPullTrickScene.vertices(g, p, alpha: 1, into: &buffers[2]) { layers.append((buffers[2], 1)) }
        under.layers = layers
        under.number += 1
        // Thrown back on, the cloth is above the dishes: drawn over them, fading under as it lands.
        var overLayers: [(vertices: [Float], alpha: Float)] = []
        if p.lift > 8 {
            let k = M.clamp((p.lift - 8) / 52, 0, 1)
            let a = k * k * (3 - 2 * k)
            if TableclothPullTrickScene.vertices(g, p, alpha: a, into: &overBuffer) { overLayers.append((overBuffer, Float(a))) }
        }
        if !overLayers.isEmpty || !over.layers.isEmpty {
            over.layers = overLayers
            over.number += 1
        }
    }

    private func take() {
        if mode != 1 {
            // Take over from the loop (or the reset) where it is.
            let p = pose
            user = User()
            user.d = p.d >= M.out - 1 ? 0 : p.d
            user.slide = p.slide
            user.count = floor(p.count + 0.5)
            user.shown = p.count
        }
        mode = 1
        loopT = -1
        lastTouch = now
    }

    func drag(_ v: DragGesture.Value, g: TableclothPullTrickGeo) {
        let t = now
        if !touchStarted {
            touchStarted = true
            let frontY = g.point(0, 0, 0).y
            guard v.startLocation.y >= frontY - 50 && v.startLocation.y <= g.panelTop else {
                grab = nil
                return
            }
            take()
            if user.whipAt >= 0 || user.d >= M.out - 1 {
                grab = nil
                return
            }
            pulling = true
            grab = user.d
            grabFeel.impactOccurred()
        }
        guard let start = grab else { return }
        lastTouch = t
        let vy = Double(v.velocity.height)
        var s = user
        let raw = start + Double(v.translation.height) * 1.15
        let d = raw < 0 ? raw / (1 - raw / 30) : min(raw, M.clothLen + 40)
        let delta = d - s.d
        let fast = vy > Self.fast
        if delta > 0 {
            // Friction drags the dishes along with a slow pull; a fast one leaves them behind.
            s.slide = min(Self.maxSlide, s.slide + delta * (fast ? 0.02 : 0.85))
            s.tipV += delta * (fast ? 3 : 8)
        }
        s.v = vy * 1.15
        s.wave = M.clamp(abs(vy) / 120, 0, 12)
        s.fling = M.clamp(vy / 3000, 0, 0.6)
        s.d = d
        user = s
    }

    func release(_ v: DragGesture.Value, reduced: Bool) {
        touchStarted = false
        guard grab != nil else { return }
        grab = nil
        pulling = false
        let t1 = now
        lastTouch = t1
        var s = user
        let vy = Double(v.velocity.height)
        if vy > Self.fast && s.d > 16 {
            let clean = s.slide < 12
            s.whipAt = reduced ? t1 - M.yank - 2 : t1
            s.whipFrom = s.d
            s.jig = clean ? 1 : 1.5
            if clean {
                s.count += 1
                s.cleanAt = t1 + 0.25
                cleanFeel.notificationOccurred(.success)
            } else {
                messyFeel.impactOccurred()
            }
        } else {
            s.v = min(s.v, 0)
            s.tipV -= 60
        }
        user = s
    }
}
