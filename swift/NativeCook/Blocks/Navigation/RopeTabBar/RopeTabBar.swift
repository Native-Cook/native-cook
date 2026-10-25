import SwiftUI
import UIKit

/*
 Rope Tab Bar — "Basecamp", an outdoor app whose tab indicator is a knot on a climbing rope tied
 across the top of the tab bar. Change tab and the knot slides along: the rope sags under it,
 lifts where it left, whips and rings down; the icons it passes are tugged down a few points.
 The pages cross-slide with a parallax and the large title cross-fades as it slides.

 One Canvas over the bar (RopeTabBarArt): the bar rendered once, the rope (48 samples, a braid of
 ~85 strands in two paths), the knot and the tap ripple drawn per frame. The rope is a closed-form
 function of time in every mode (RopeTabBarMotion): a sag, the knot's weight and four string
 modes rung by "kicks". autoPlay: loop = 8 s, Explore → Gear → Routes → Explore. Under the
 finger: tap a tab, or drag the knot along the rope (a tick per tab, snaps on release).

 Modes: 0 loop · 1 touch (knot on a spring toward its tab) · 2 easing back to Explore 2 s after
 the last touch (0.9 s), then the loop restarts.
 */
struct RopeTabBar: View {
    /// Plays the 8 s demo: Explore → Gear → Routes → Explore.
    var autoPlay = false

    static let loop = RopeTabBarMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = RopeTabBarEngine()
    /// Keeps the timeline running while the knot or the rope still moves (outside autoPlay).
    @State private var active = false

    fileprivate static let background = Color(hex: 0x13201A)
    private static let barContent = 78.0
    private static let tabNames = ["Explore", "Routes", "Gear", "Profile"]
    private static let icons = ["mountain.2.fill", "map.fill", "backpack.fill", "person.crop.circle.fill"]
    private static let activeColor = Color(hex: 0xF4EAD8)
    private static let idleColor = Color(hex: 0x7F9387)

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
        .background(Self.background.ignoresSafeArea())
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let w = size.width
        let barH = Self.barContent + insets.bottom
        let barTop = size.height - barH
        let pagesTop = insets.top + 108
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let bar = engine.bar(w: w, barH: barH, scale: displayScale)
        let topPad = RopeTabBarArt.topPad
        return TimelineView(.animation(paused: !auto && !active)) { timeline in
            let f = engine.step(to: timeline.date, auto: auto, reduced: reduced)
            ZStack(alignment: .topLeading) {
                header(p: f.p)
                    .frame(width: w - 40, alignment: .leading)
                    .offset(x: 20, y: insets.top + 10)
                    .allowsHitTesting(false)

                RopeTabBarPages(p: f.p, width: w)
                    .frame(width: w * 4, alignment: .topLeading)
                    .offset(x: -f.p * w)
                    .frame(width: w, height: max(0, barTop - pagesTop), alignment: .topLeading)
                    .clipped()
                    .offset(y: pagesTop)

                ZStack(alignment: .topLeading) {
                    Canvas { c, _ in
                        c.draw(bar, in: CGRect(x: 0, y: 0, width: w, height: barH + topPad))
                        drawRope(c, w: w, f: f)
                    }
                    .frame(width: w, height: barH + topPad)
                    ForEach(0..<RopeTabBarMotion.tabs, id: \.self) { i in
                        tab(i, p: f.p, tug: f.tug[i])
                            .frame(width: w / 4, height: 52, alignment: .top)
                            .offset(x: Double(i) * w / 4, y: topPad + 26)
                    }
                }
                .frame(width: w, height: barH + topPad, alignment: .topLeading)
                .contentShape(Rectangle())
                .coordinateSpace(name: "ropebar")
                .gesture(touch(w: w, reduced: reduced))
                .offset(y: barTop - topPad)
                .accessibilityElement()
                .accessibilityAddTraits(.isTabBar)
                .accessibilityLabel("Tabs, \(Self.tabNames[Int(min(3, max(0, f.p)).rounded())]) selected")
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .onChange(of: f.idle) { _, idle in
                if idle { active = false }
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    private func drawRope(_ c: GraphicsContext, w: Double, f: RopeTabBarFrame) {
        let M = RopeTabBarMotion.self
        let base = RopeTabBarArt.topPad + RopeTabBarArt.ropeLine
        let uk = M.tabU(f.p)
        let n = RopeTabBarArt.samples
        let ys = (0...n).map { base + M.ropeAt(Double($0) / Double(n), uk, f.kicks, f.clock, f.gain) }
        let ky = base + M.ropeAt(uk, uk, f.kicks, f.clock, f.gain)
        let slope = (M.ropeAt(uk + 0.01, uk, f.kicks, f.clock, f.gain) - M.ropeAt(uk - 0.01, uk, f.kicks, f.clock, f.gain)) / (0.02 * w)
        let life = M.rippleAt(f.clock, f.ripple.0)
        RopeTabBarArt.drawRope(c, w: w, ys: ys, kx: uk * w, ky: ky + 1, slope: slope, ripple: life, rx: M.tabU(f.ripple.1) * w, ry: RopeTabBarArt.topPad + 40)
    }

    private func header(p: Double) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("BASECAMP")
                    .font(.system(size: 12, weight: .heavy))
                    .kerning(2.6)
                    .foregroundStyle(RopeTabBarStyle.accent)
                Spacer(minLength: 0)
                HStack(spacing: 6) {
                    Circle()
                        .fill(Color(hex: 0xF2B766))
                        .frame(width: 10, height: 10)
                        .shadow(color: Color(hex: 0xF2B766, opacity: 0.7), radius: 4)
                    Text("14° Clear")
                        .font(.system(size: 13, weight: .semibold).monospacedDigit())
                        .foregroundStyle(RopeTabBarStyle.ink)
                }
                .padding(.horizontal, 12)
                .frame(height: 30)
                .background(Capsule().fill(Color(.sRGB, red: 242 / 255, green: 235 / 255, blue: 221 / 255, opacity: 0.07)))
            }
            .frame(height: 32)
            ZStack(alignment: .topLeading) {
                ForEach(0..<4, id: \.self) { i in
                    let d = p - Double(i)
                    let a = abs(d)
                    Text(Self.tabNames[i])
                        .font(.system(size: 34, weight: .bold))
                        .kerning(-0.6)
                        .foregroundStyle(RopeTabBarStyle.ink)
                        .fixedSize()
                        .scaleEffect(1 - 0.06 * min(1, a), anchor: .leading)
                        .offset(x: -d * 26)
                        .opacity(max(0, 1 - a * 1.6))
                }
            }
            .frame(height: 44, alignment: .topLeading)
        }
    }

    /// One tab: an icon and a label in idle and active colours, cross-faded, tugged by the rope.
    private func tab(_ i: Int, p: Double, tug: Double) -> some View {
        let on = max(0, 1 - abs(p - Double(i)))
        let off = min(1, abs(p - Double(i)))
        return VStack(spacing: 6) {
            ZStack {
                icon(i, Self.idleColor).opacity(off)
                icon(i, Self.activeColor).opacity(on)
            }
            .frame(width: 30, height: 28)
            .offset(y: tug)
            ZStack {
                label(i, Self.idleColor).opacity(off)
                label(i, Self.activeColor).opacity(on)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 16)
        }
        .allowsHitTesting(false)
    }

    private func icon(_ i: Int, _ color: Color) -> some View {
        Image(systemName: Self.icons[i])
            .resizable()
            .scaledToFit()
            .frame(width: 24, height: 24)
            .foregroundStyle(color)
    }

    private func label(_ i: Int, _ color: Color) -> some View {
        Text(Self.tabNames[i])
            .font(.system(size: 11, weight: .semibold))
            .foregroundStyle(color)
            .lineLimit(1)
    }

    private func touch(w: Double, reduced: Bool) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .named("ropebar"))
            .onChanged { value in
                active = true
                engine.touchMoved(start: value.startLocation, at: value.location, velocityX: value.velocity.width, w: w, reduced: reduced)
            }
            .onEnded { value in
                engine.touchEnded(start: value.startLocation, at: value.location, w: w, reduced: reduced)
            }
    }
}

/// What one frame shows.
struct RopeTabBarFrame {
    var p: Double
    var kicks: [RopeTabBarKick]
    var clock: Double
    var gain: Double
    /// Start time (on the kick clock) and tab of the latest tap ripple.
    var ripple: (Double, Double)
    var tug: [Double]
    /// The knot sits on its tab and the rope has rung out (lets the timeline pause outside autoPlay).
    var idle: Bool
}

/// The knot, the kicks, the autoPlay clock and the touch, stepped once a frame.
@MainActor
final class RopeTabBarEngine {
    private typealias M = RopeTabBarMotion

    private static let returnAfter = 2.0
    private static let returnFor = 0.9
    private static let maxKicks = 8

    private var barImage: Image?
    private var barKey: (Double, Double, Double)?

    private var now = 0.0
    private var last: Double?
    private var loopStart: Double?
    private var mode: Int?
    private var p = 0.0
    private var pVel = 0.0
    private var target = 0.0
    private var dragging = false
    /// The touch began on the knot and may become a drag; or it is a tap.
    private var onKnot: Bool?
    /// Kicks in the touch timeline.
    private var touchKicks: [RopeTabBarKick] = []
    private var ripple = (-10.0, 0.0)
    private var lastTouch = 0.0
    private var back = (0.0, 0.0)
    private var lastFrame: RopeTabBarFrame?

    private static let tapFeel = UIImpactFeedbackGenerator(style: .light)
    private static let tickFeel = UISelectionFeedbackGenerator()

    private static func clock() -> Double {
        Date().timeIntervalSinceReferenceDate
    }

    func bar(w: Double, barH: Double, scale: Double) -> Image {
        if let barImage, let k = barKey, k == (w, barH, scale) { return barImage }
        let made = RopeTabBarArt.renderBar(w: w, barH: barH, scale: scale)
        barImage = made
        barKey = (w, barH, scale)
        return made
    }

    func step(to date: Date, auto: Bool, reduced: Bool) -> RopeTabBarFrame {
        let t0 = date.timeIntervalSinceReferenceDate
        if let last, t0 <= last, let lastFrame { return lastFrame }
        let dt = min(last.map { t0 - $0 } ?? 1 / 60, 1 / 30)
        last = t0
        now = t0
        if loopStart == nil { loopStart = t0 }
        if mode == nil { mode = auto ? 0 : 1 }

        var kicks: [RopeTabBarKick]
        var kickClock: Double
        var gain = 1.0
        var idle = false
        if mode == 0 {
            let t = (t0 - (loopStart ?? t0)).truncatingRemainder(dividingBy: M.loop)
            p = M.loopKnot(t)
            kicks = M.loopKicks
            kickClock = t
            var r = (-10.0, 0.0)
            for m in M.loopMoves where M.rippleAt(t, m.0) >= 0 { r = (m.0, m.2) }
            ripple = r
        } else {
            kicks = touchKicks
            kickClock = t0
            if mode == 1 {
                if !dragging {
                    // The knot rides a spring to its tab (ω 7.5, ζ 0.78), carrying the release velocity.
                    var x = p
                    var v = pVel
                    let h = dt / 3
                    for _ in 0..<3 {
                        v += (-M.knotW * M.knotW * (x - target) - 2 * M.knotZ * M.knotW * v) * h
                        x += v * h
                    }
                    if reduced {
                        x = target
                        v = 0
                    }
                    p = x
                    pVel = v
                    let still = abs(x - target) < 0.005
                    if auto && t0 - lastTouch > Self.returnAfter && still {
                        mode = 2
                        back = (t0, x)
                    }
                    let rung = touchKicks.allSatisfy { t0 - $0.time >= 1.9 } && M.rippleAt(t0, ripple.0) < 0
                    if !auto && still && abs(v) < 0.01 && rung && onKnot == nil {
                        p = target
                        pVel = 0
                        idle = true
                    }
                }
            } else {
                let k = M.easeInOut(M.seg(t0 - back.0, 0, Self.returnFor))
                p = back.1 * (1 - k)
                gain = 1 - k
                if k >= 1 {
                    mode = 0
                    loopStart = t0
                    touchKicks = []
                }
            }
        }
        // Icons are tugged by how far the rope hangs below its slack line at their x.
        let uk = M.tabU(p)
        let tug = (0..<M.tabs).map { i -> Double in
            let u = M.tabU(Double(i))
            let y = M.ropeAt(u, uk, kicks, kickClock, gain) - M.sag * sin(Double.pi * u)
            return min(6, max(-3, y * 0.45))
        }
        let frame = RopeTabBarFrame(p: p, kicks: kicks, clock: kickClock, gain: gain, ripple: ripple, tug: tug, idle: idle)
        lastFrame = frame
        return frame
    }

    private func addKick(_ at: Double, _ u: Double, _ size: Double) {
        touchKicks = Array(touchKicks.suffix(Self.maxKicks - 1)) + [RopeTabBarKick(time: at, u: u, size: size)]
    }

    private func takeOver() {
        guard mode == 0 else { return }
        // Freeze the loop's place and ringing into the touch timeline.
        let t = (now - (loopStart ?? now)).truncatingRemainder(dividingBy: M.loop)
        let shift = now - t
        touchKicks = M.loopKicks.map { RopeTabBarKick(time: $0.time + shift, u: $0.u, size: $0.size) }
        pVel = 0
        target = p.rounded()
        mode = 1
    }

    func touchMoved(start: CGPoint, at loc: CGPoint, velocityX: Double, w: Double, reduced: Bool) {
        let cell = w / Double(M.tabs)
        guard cell > 0 else { return }
        let t = Self.clock()
        if onKnot == nil {
            let kx = M.tabU(p) * w
            onKnot = abs(start.x - kx) <= 40 && start.y <= RopeTabBarArt.topPad + 40
        }
        if !dragging {
            guard onKnot == true, abs(loc.x - M.tabU(p) * w) > 4 else { return }
            takeOver()
            mode = 1
            dragging = true
            pVel = 0
        }
        var q = loc.x / cell - 0.5
        if q < 0 { q = -(-q / (1 - q / 0.6)) * 0.55 }
        let top = Double(M.tabs - 1)
        if q > top {
            let o = q - top
            q = top + o / (1 + o / 0.6) * 0.55
        }
        let before = p.rounded()
        let vel = velocityX / cell
        pVel = vel
        p = q
        let after = min(top, max(0, q)).rounded()
        if after != before {
            addKick(t, M.tabU(after), min(4, abs(vel) * 0.8))
            Self.tickFeel.selectionChanged()
        }
        lastTouch = t
    }

    func touchEnded(start: CGPoint, at loc: CGPoint, w: Double, reduced: Bool) {
        let cell = w / Double(M.tabs)
        let t = Self.clock()
        onKnot = nil
        if dragging {
            dragging = false
            let q = p
            let v = reduced ? 0 : max(-6, min(6, pVel))
            let tab = min(Double(M.tabs - 1), max(0, (q + v * 0.15).rounded()))
            target = tab
            pVel = v
            if !reduced { addKick(t + 0.3, M.tabU(tab), 2 + 3.5 * pow(abs(tab - q), 0.6)) }
            lastTouch = t
            Self.tapFeel.impactOccurred()
            return
        }
        // A tap: the finger stayed within 12 pt.
        guard cell > 0, hypot(loc.x - start.x, loc.y - start.y) <= 12 else { return }
        takeOver()
        mode = 1
        let tab = min(Double(M.tabs - 1), max(0, floor(loc.x / cell)))
        let from = p
        let d = pow(abs(tab - from), 0.6)
        if !reduced && abs(tab - from) > 0.05 {
            addKick(t + 0.05, M.tabU(from), -3.2 * d)
            addKick(t + 0.42, M.tabU(tab), 6 * d)
        }
        target = tab
        ripple = (t, tab)
        lastTouch = t
        Self.tapFeel.impactOccurred()
    }
}
