import SwiftUI
import UIKit

/*
 Wind Scatter Letters — the hero of an autumn weather edition: the headline "Let it drift" blows
 apart like leaves in a gust and settles back. Each glyph's outline is measured once and placed
 per frame with its own position, rotation and scale; every third glyph sits further back,
 smaller and softened. Leaves drawn in code lie on the ridge and cross the sky with the gust.
 A swipe is a gust in that direction at that speed: glyphs and leaves near the finger scatter
 most, and soft springs bring them home.

 The sky is a Canvas that never changes, so SwiftUI draws it once. Over it one live Canvas draws
 the wind streaks, four glyph batches (two motion trails, far, near: one path each) and the
 leaves in three colour batches. Copy and controls are views. The script is in
 WindScatterLettersMotion.swift.
*/
struct WindScatterLetters: View {
    /// Plays the gust: build, scatter, return, every `loop` seconds.
    var autoPlay = false

    static let loop = 9.0

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            WindScatterLettersStage(autoPlay: autoPlay, size: size, insetTop: insets.top, insetBottom: insets.bottom)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(WindScatterLettersArt.hex(0x25202A).ignoresSafeArea())
    }
}

private struct WindScatterLettersStage: View {
    let autoPlay: Bool
    let size: CGSize
    let insetTop: Double
    let insetBottom: Double

    private typealias A = WindScatterLettersArt
    private typealias M = WindScatterLettersMotion

    @Environment(\.accessibilityReduceMotion) private var reduced
    @State private var model = WindScatterLettersModel()
    @State private var awake = false

    var body: some View {
        let w = size.width
        let h = size.height
        let auto = autoPlay && !reduced
        model.configure(w: w, h: h, insetTop: insetTop, auto: auto, onSleep: { awake = false })
        let lay = model.layout
        return ZStack(alignment: .topLeading) {
            WindScatterLettersSky(w: w, h: h)
                .equatable()
                .frame(width: w, height: h)

            TimelineView(.animation(paused: !(auto || awake))) { timeline in
                scene(w: w, h: h, date: timeline.date)
                    .frame(width: w, height: h)
            }
            .allowsHitTesting(false)

            // The swipe surface, apart from the timeline so the drawing stays a plain Canvas.
            Color.clear
                .frame(width: w, height: h)
                .contentShape(Rectangle())
                .gesture(gust)
                .accessibilityElement()
                .accessibilityAddTraits(.isHeader)
                .accessibilityLabel("Let it drift. Swipe across the headline to blow the letters away.")

            masthead
                .frame(width: max(0, w - 48))
                .offset(x: 24, y: insetTop + 24)
                .allowsHitTesting(false)

            deck(showHourly: lay.b2 + 34 + 292 <= h * 0.78)
                .frame(width: max(0, w - 48), alignment: .topLeading)
                .offset(x: 24, y: lay.b2 + 34)
                .allowsHitTesting(false)

            cta
                .frame(width: max(0, w - 48))
                .offset(x: 24, y: h - insetBottom - 20 - 52)
        }
        .frame(width: w, height: h, alignment: .topLeading)
        .clipped()
    }

    private var gust: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                if !awake { awake = true }
                model.drag(v)
            }
            .onEnded { _ in model.release() }
    }

    // MARK: Drawing

    private func scene(w: Double, h: Double, date: Date) -> some View {
        // Everything the frame draws is computed here, as values the Canvas captures: a Canvas
        // that only captured the model (a reference) would look unchanged to SwiftUI and never
        // be drawn again.
        let m = model
        m.frame(date)
        let air = m.air
        var streaks: [(Path, Double)] = []
        if air > 0.001 {
            for (i, p) in m.streaks.enumerated() {
                let travel = m.streakTravel(i)
                let head = min(1, travel)
                let start = min(1, max(0, travel - 0.32))
                guard head > start else { continue }
                streaks.append((p.trimmedPath(from: start, to: head), 0.32 * air * (0.5 + 0.5 * M.hash(Double(i) + 3))))
            }
        }
        let trail2 = air > 0.001 ? m.glyphBatch(pose: 2, pick: 2) : Path()
        let trail1 = air > 0.001 ? m.glyphBatch(pose: 1, pick: 2) : Path()
        let far = m.glyphBatch(pose: 0, pick: 0)
        let near = m.glyphBatch(pose: 0, pick: 1)
        let leaves = m.leafBatch()
        return Canvas { c, _ in
            _ = date
            // Wind streaks.
            for (p, o) in streaks {
                var l = c
                l.opacity = o
                l.stroke(p, with: .color(A.rgba(255, 228, 196, 1)), style: StrokeStyle(lineWidth: 1.2, lineCap: .round))
            }
            // Two motion trails, the far glyphs (softened) and the near ones.
            if air > 0.001 {
                var t2 = c
                t2.opacity = 0.09 * air
                t2.fill(trail2, with: .color(A.cream))
                var t1 = c
                t1.opacity = 0.2 * air
                t1.fill(trail1, with: .color(A.cream))
            }
            c.drawLayer { l in
                // Keep the blurred layer to the far glyphs' own bounds.
                l.clip(to: Path(far.boundingRect.insetBy(dx: -12, dy: -12)))
                l.addFilter(.blur(radius: 0.3 + 2.2 * air))
                l.fill(far, with: .color(A.rgba(236, 214, 190, 0.82)))
            }
            c.fill(near, with: .color(A.cream))
            // Leaves.
            for k in 0..<3 { c.fill(leaves.0[k], with: .color(A.leaves[k])) }
            c.stroke(leaves.1, with: .color(A.rgba(60, 24, 12, 0.55)), lineWidth: 0.8)
        }
    }

    // MARK: Views

    private var masthead: some View {
        HStack {
            Text("AUTUMN EDITION")
                .font(.system(size: 11, weight: .bold))
                .kerning(3)
                .foregroundStyle(A.ember)
            Spacer(minLength: 8)
            Text("No. 14 · Sat 25 Oct")
                .font(.system(size: 12, weight: .medium).monospacedDigit())
                .foregroundStyle(A.rgba(244, 231, 212, 0.55))
        }
    }

    private func deck(showHourly: Bool) -> some View {
        VStack(alignment: .leading, spacing: 20) {
            Text("Gusts up to 48 km/h from the west. Leaves down by noon, clear skies by four.")
                .font(.system(size: 17))
                .lineSpacing(24 - 17 * 1.193)
                .foregroundStyle(A.creamSoft)
                .fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 8) {
                stat("Wind", "32", "km/h W")
                stat("Gusts", "48", "km/h")
                stat("Feels", "9°", "at 15:00")
            }
            if showHourly { hourly }
        }
    }

    private func stat(_ label: String, _ value: String, _ unit: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label.uppercased())
                .font(.system(size: 10, weight: .bold))
                .kerning(1.4)
                .foregroundStyle(A.rgba(244, 231, 212, 0.5))
            Text(value)
                .font(.system(size: 22, weight: .semibold).monospacedDigit())
                .foregroundStyle(A.cream)
            Text(unit)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(A.rgba(244, 231, 212, 0.55))
                .lineLimit(1)
        }
        .padding(12)
        .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(A.rgba(255, 236, 214, 0.06)))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(A.rgba(255, 236, 214, 0.16), lineWidth: 1 / 3))
    }

    private static let hours: [(at: String, kmh: Double)] = [
        ("12:00", 24), ("13:00", 31), ("14:00", 40), ("15:00", 48), ("16:00", 37), ("17:00", 22),
    ]

    /// Gusts over the afternoon: one bar per hour, the peak in ember.
    private var hourly: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("GUSTS THIS AFTERNOON")
                .font(.system(size: 10, weight: .bold))
                .kerning(1.4)
                .foregroundStyle(A.rgba(244, 231, 212, 0.5))
            HStack(alignment: .bottom) {
                ForEach(0..<Self.hours.count, id: \.self) { n in
                    let h = Self.hours[n]
                    VStack(spacing: 4) {
                        Text("\(Int(h.kmh))")
                            .font(.system(size: 12, weight: .semibold).monospacedDigit())
                            .foregroundStyle(A.rgba(244, 231, 212, 0.8))
                        RoundedRectangle(cornerRadius: 3)
                            .fill(h.kmh == 48 ? A.ember : A.rgba(244, 231, 212, 0.32))
                            .frame(width: 6, height: 6 + (h.kmh / 48) * 26)
                        Text(h.at)
                            .font(.system(size: 11, weight: .medium).monospacedDigit())
                            .foregroundStyle(A.rgba(244, 231, 212, 0.45))
                    }
                    .frame(width: 44)
                    if h.at != "17:00" { Spacer(minLength: 0) }
                }
            }
        }
    }

    private var cta: some View {
        HStack(spacing: 8) {
            Image(systemName: "wind").font(.system(size: 17, weight: .medium))
            Text("Read the forecast").font(.system(size: 17, weight: .semibold))
        }
        .foregroundStyle(A.hex(0x3A2418))
        .frame(maxWidth: .infinity)
        .frame(height: 52)
        .background(LinearGradient(
            stops: [.init(color: A.hex(0xF7EAD6), location: 0), .init(color: A.hex(0xEFD8B8), location: 0.5), .init(color: A.hex(0xE2C196), location: 1)],
            startPoint: .top, endPoint: .bottom))
        .clipShape(Capsule())
        .overlay(Capsule().strokeBorder(LinearGradient(colors: [.white.opacity(0.7), .white.opacity(0)], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.12)), lineWidth: 1))
        .shadow(color: .black.opacity(0.4), radius: 1, y: 1)
        .shadow(color: .black.opacity(0.35), radius: 15, y: 12)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }
}

/// The sky and ridges, rasterised once per size into a bitmap (its cloud blurs and grain are
/// too heavy to draw again whenever the scene above it changes).
private struct WindScatterLettersSky: View, Equatable {
    let w: Double
    let h: Double

    @Environment(\.displayScale) private var displayScale

    @MainActor private static var cache: [String: Image] = [:]

    nonisolated static func == (a: Self, b: Self) -> Bool { a.w == b.w && a.h == b.h }

    var body: some View {
        Self.image(w: w, h: h, scale: displayScale)
            .resizable()
            .frame(width: w, height: h)
    }

    @MainActor
    private static func image(w: Double, h: Double, scale: Double) -> Image {
        let key = "\(w)x\(h)@\(scale)"
        if let hit = cache[key] { return hit }
        let view = Canvas { c, _ in
            WindScatterLettersArt.drawBackground(&c, w: w, h: h)
        }
        .frame(width: max(1, w), height: max(1, h))
        let r = ImageRenderer(content: view)
        r.scale = scale
        r.isOpaque = true
        let img = Image(uiImage: r.uiImage ?? UIImage())
        cache[key] = img
        return img
    }
}

/// The autoPlay clock and the touch simulation, stepped once a frame like the RN frame callback.
@MainActor
final class WindScatterLettersModel {
    private typealias M = WindScatterLettersMotion
    private static let idle = 2.0
    private static let trailLag = [0.045, 0.09]

    private(set) var layout = WindScatterLettersLayout(w: 393, h: 852, insetTop: 59)
    private(set) var streaks: [Path] = []
    private var w = 0.0, h = 0.0, insetTop = -1.0
    private var auto = false
    private var onSleep: () -> Void = {}

    private var last: Date?
    private var clock = 0.0
    /// 0 = autoPlay (or rest), 1 = the simulation.
    private var mode = 0
    private var idleFor = 0.0
    private var sim: WindScatterLettersSim?
    private var wind = WindScatterLettersWind()
    private var ticked = false
    private var dragging = false
    /// Every body's pose now, and at two earlier moments for the trails.
    private var poses: [[WindScatterLettersPose]] = [[], [], []]
    private(set) var air = 0.0

    private let tick = UIImpactFeedbackGenerator(style: .light)

    func configure(w: Double, h: Double, insetTop: Double, auto: Bool, onSleep: @escaping () -> Void) {
        if auto != self.auto { clock = 0 }
        self.auto = auto
        self.onSleep = onSleep
        if w != self.w || h != self.h || insetTop != self.insetTop {
            self.w = w
            self.h = h
            self.insetTop = insetTop
            layout = WindScatterLettersLayout(w: w, h: h, insetTop: insetTop)
            streaks = WindScatterLettersArt.streaks(w: w, h: h)
            sim = nil
            mode = 0
            updatePoses()
        }
    }

    func frame(_ date: Date) {
        let raw = last.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        last = date
        let dt = min(max(raw, 0), 0.05)
        if dt > 0 { step(dt) }
        updatePoses()
    }

    private func step(_ dt: Double) {
        if mode == 0 {
            if auto { clock = (clock + dt).truncatingRemainder(dividingBy: WindScatterLetters.loop) }
            return
        }
        guard var s = sim else { return }
        if !wind.on { idleFor += dt }
        let settle = idleFor > Self.idle
        let steps = dt > 1.0 / 90 ? 2 : 1
        for _ in 0..<steps { s.step(homes: layout.homes, wind: wind, dt: dt / Double(steps), settle: settle) }
        sim = s
        if settle && s.energy < 0.4 {
            clock = 0
            mode = 0
            sim = nil
            if !auto {
                let sleep = onSleep
                DispatchQueue.main.async { sleep() }
            }
        }
    }

    private func updatePoses() {
        let n = layout.homes.count
        var out: [[WindScatterLettersPose]] = [[], [], []]
        if mode == 0 || sim == nil {
            let times = [clock, clock - Self.trailLag[0], clock - Self.trailLag[1]]
            for k in 0..<3 {
                out[k].reserveCapacity(n)
                for i in 0..<n {
                    out[k].append(auto ? M.autoOffset(max(0, times[k]), i, home: layout.homes[i], w: w, h: h) : WindScatterLettersPose())
                }
            }
            air = auto ? M.gustAt(clock) : 0
        } else if let s = sim {
            for i in 0..<n {
                let d = M.depthOf(i)
                func sc(_ x: Double, _ y: Double) -> Double { 1 + d * min(1, hypot(x, y) / 220) }
                out[0].append(WindScatterLettersPose(x: s.x[i], y: s.y[i], r: s.r[i], s: sc(s.x[i], s.y[i])))
                out[1].append(WindScatterLettersPose(x: s.px[0][i], y: s.py[0][i], r: s.r[i], s: sc(s.px[0][i], s.py[0][i])))
                out[2].append(WindScatterLettersPose(x: s.px[1][i], y: s.py[1][i], r: s.r[i], s: sc(s.px[1][i], s.py[1][i])))
            }
            var v = 0.0
            for i in 0..<layout.glyphs { v = max(v, hypot(s.vx[i], s.vy[i])) }
            air = min(1, v / 600)
        }
        poses = out
    }

    /// One wind streak's travel: a segment 0.32 of the path long runs along it, in on the left and
    /// out on the right, twice.
    func streakTravel(_ i: Int) -> Double {
        if mode != 0 || !auto { return 0 }
        let a = 1.6 + Double(i) * 0.13
        let p1 = M.seg(clock, a, a + 1.1)
        let p = p1 < 1 ? p1 : M.seg(clock, a + 1.4, a + 2.5)
        return M.easeInOutCubic(p) * 1.32
    }

    /// The glyphs of one pose set as one path. pick 0 = far glyphs, 1 = near glyphs, 2 = all.
    func glyphBatch(pose k: Int, pick: Int) -> Path {
        var out = Path()
        let L = layout
        guard poses[k].count >= L.glyphs else { return out }
        for i in 0..<L.glyphs {
            let far = M.depthOf(i) < 0
            if pick == 0 && !far { continue }
            if pick == 1 && far { continue }
            let p = poses[k][i]
            let home = L.homes[i]
            let t = CGAffineTransform(translationX: -L.px[i], y: -L.py[i])
                .concatenating(CGAffineTransform(scaleX: p.s, y: p.s))
                .concatenating(CGAffineTransform(rotationAngle: p.r))
                .concatenating(CGAffineTransform(translationX: home.x + p.x, y: home.y + p.y))
            out.addPath(L.paths[i], transform: t)
        }
        return out
    }

    /// The leaves in three colour batches, and their ribs.
    func leafBatch() -> ([Path], Path) {
        var colors = [Path(), Path(), Path()]
        var ribs = Path()
        let L = layout
        var items: [(Double, Double, Double, Double, Double)] = []
        guard poses[0].count == L.homes.count else { return (colors, ribs) }
        for j in 0..<M.groundLeaves {
            let i = L.glyphs + j
            let home = L.homes[i]
            let p = poses[0][i]
            let rest = (M.hash(Double(j) * 2.3 + 40) - 0.5) * 1.2 + (j % 2 == 1 ? .pi : 0)
            items.append((home.x + p.x, home.y + p.y, rest + p.r * 1.6, cos(p.r * 2.2), 0.9 + 0.3 * M.hash(Double(j) + 50)))
        }
        if mode == 0 && auto {
            for j in 0..<M.flyingLeaves {
                if let f = M.flyingLeaf(clock, j, w: w, h: h) { items.append(f) } else { items.append((-200, -200, 0, 1, 1)) }
            }
        }
        for (j, it) in items.enumerated() where it.0 >= -100 {
            let t = CGAffineTransform(scaleX: it.3 * it.4, y: it.4)
                .concatenating(CGAffineTransform(rotationAngle: it.2))
                .concatenating(CGAffineTransform(translationX: it.0, y: it.1))
            colors[j % 3].addPath(WindScatterLettersArt.blade, transform: t)
            ribs.addPath(WindScatterLettersArt.rib, transform: t)
        }
        return (colors, ribs)
    }

    // MARK: Touch: a swipe is a gust

    private func takeOver() {
        if mode == 0 {
            let n = layout.homes.count
            var s = WindScatterLettersSim(n)
            for i in 0..<n {
                let o = auto ? M.autoOffset(clock, i, home: layout.homes[i], w: w, h: h) : WindScatterLettersPose()
                s.x[i] = o.x
                s.y[i] = o.y
                s.r[i] = o.r
                s.px[0][i] = o.x
                s.py[0][i] = o.y
                s.px[1][i] = o.x
                s.py[1][i] = o.y
            }
            sim = s
            mode = 1
        }
        idleFor = 0
    }

    func drag(_ v: DragGesture.Value) {
        if !dragging {
            dragging = true
            takeOver()
            wind = WindScatterLettersWind(fx: v.location.x, fy: v.location.y, fvx: 0, fvy: 0, on: true)
            ticked = false
            return
        }
        let fvx = v.velocity.width
        let fvy = v.velocity.height
        wind = WindScatterLettersWind(fx: v.location.x, fy: v.location.y, fvx: fvx, fvy: fvy, on: true)
        idleFor = 0
        if !ticked && hypot(fvx, fvy) > 1400 {
            ticked = true
            tick.impactOccurred()
        }
    }

    func release() {
        wind = WindScatterLettersWind()
        idleFor = 0
        dragging = false
    }
}

