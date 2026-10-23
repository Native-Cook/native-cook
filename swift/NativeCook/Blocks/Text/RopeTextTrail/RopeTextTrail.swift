import CoreText
import SwiftUI
import UIKit

/*
 Rope Text Trail — a sentence that behaves like a rope of letters on grained paper.

 The sentence is a chain: its head (a small orange dot just ahead of the closing dash) moves, and every
 letter sits on the path the head has drawn, its centre placed by its own advance behind the head and
 turned to the path's tangent. Letters are filled glyph outlines whose baseline runs half an x-height
 under the path, so the path threads through the middle of the lowercase letters.

 Resting curve: y = cy − A·sin(2πu) from x = 20 to x = W − 20 (u = 0 … 1, cy = 50 % of the height),
 A solved so the curve is the rope's length (sentence + 9 pt gap + dot) plus 20 pt, the rope centred on it.

 autoPlay timeline (loop = 10 s, e = seconds into the loop):
   0.0 … 1.5   rest. The rope sways once over the rest window (7.7 → 11.5, wrapping): the middle of the
               curve sags up to 9 pt and lifts back, a(φ) = sin(2πφ)·sin(πφ) / 0.77, so it starts and stops still.
   1.5 … 7.7   the head draws its path: up the right side, a loop over the top leftwards, down across the
               middle, a loop along the bottom leftwards, up the left edge, then along the resting curve itself
               back to its place. Arc length follows easeInOutSine, so it starts and arrives at rest. Speed m =
               speed / 700 pt/s (≤ 1) stretches the spacing by up to 10 %, and the tail letters shrink by up to
               18 % and fade by up to 40 % (linearly less towards the head).
   7.7 … 10.0  rest; t = 10 is the same frame as t = 0 (the sway is zero at both ends of its window).

 Touch: dragging anywhere grabs the head; it glides onto the finger (time constant 0.14 s) and the rope
 follows the path it draws (a light haptic on grab). On release every letter springs to its place on the
 resting curve (0.9 s each, damped spring with a 4 % overshoot, the head first and the tail 0.3 s later,
 a soft haptic). 2 s after the release the loop starts again from rest.
 Reduced motion: the rope rests on the curve without swaying and the loop is off; a drag still pulls it,
 and on release it fades back onto the curve (0.35 s).
 */

struct RopeTextTrail: View {
    var text = "words follow you wherever you go —"
    var accent = "you"
    var label = "A LINE OF THOUGHT"
    var issue = "No. 07"
    var hint = "Drag to write in the air"
    var autoPlay = false

    static let loop: Double = 10
    static let moveStart: Double = 1.5
    static let moveEnd: Double = 7.7
    static let fontSize: Double = 32
    static let paper = Color(red: 0.949, green: 0.937, blue: 0.910) // #F2EFE8
    static let ink = Color(red: 0.102, green: 0.094, blue: 0.078) // #1A1814
    static let accentColor = Color(red: 1.0, green: 0.302, blue: 0.122) // #FF4D1F

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = RopeTextTrailModel()
    @State private var autoStart: Date? = nil
    @State private var dragging = false
    @State private var fading = false
    @State private var resume: Task<Void, Never>? = nil
    @State private var grabHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var releaseHaptic = UIImpactFeedbackGenerator(style: .soft)

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            let layout = RopeTextTrailLayout(text: text, accent: accent, size: size)
            // Read here so that a change re-renders the timeline's content.
            let start = autoStart
            let held = dragging
            ZStack(alignment: .topLeading) {
                Self.paper
                RopeTextTrailGrain()
                    .frame(width: size.width, height: size.height)
                TimelineView(.animation(paused: reduceMotion && !dragging && !fading)) { timeline in
                    let rope = placements(layout: layout, now: timeline.date, autoStart: start, dragging: held)
                    ropeCanvas(layout: layout, rope: rope)
                }
                .frame(width: size.width, height: size.height)
                .contentShape(Rectangle())
                .gesture(drag(layout: layout))
                .accessibilityElement()
                .accessibilityLabel(text)
                chrome(size: size, insets: insets)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onAppear {
            grabHaptic.prepare()
            if autoPlay && !reduceMotion { autoStart = .now }
        }
        .onDisappear { resume?.cancel() }
    }

    // MARK: Placement

    private func placements(layout: RopeTextTrailLayout, now: Date, autoStart: Date?, dragging: Bool) -> RopeTextTrailRope {
        if dragging || model.touching {
            return model.touchRope(layout: layout, now: now)
        }
        if let start = model.relaxStart {
            let rest = layout.restRope(sway: 0)
            if reduceMotion { return rest }
            let elapsed = now.timeIntervalSince(start)
            if elapsed < RopeTextTrailModel.relaxSpan { return model.relaxRope(to: rest, elapsed: elapsed) }
        }
        if let autoStart, now >= autoStart, !reduceMotion {
            let e = now.timeIntervalSince(autoStart).truncatingRemainder(dividingBy: Self.loop)
            return layout.autoRope(e: e)
        }
        if !autoPlay && !reduceMotion {
            // Without the loop the resting rope keeps swaying, one sway every 3.8 s.
            let since = now.timeIntervalSince(model.restSince)
            let phi = (since / 3.8).truncatingRemainder(dividingBy: 1)
            return layout.restRope(sway: RopeTextTrailLayout.sway(phi))
        }
        return layout.restRope(sway: 0)
    }

    // MARK: Drawing

    private func ropeCanvas(layout: RopeTextTrailLayout, rope: RopeTextTrailRope) -> some View {
        let fade = model.fadeIn(now: .now, reduced: reduceMotion)
        return Canvas { ctx, _ in
            ctx.opacity = fade
            for (i, p) in rope.letters.enumerated() where layout.chars[i] != " " {
                guard let glyph = layout.glyphs[i] else { continue }
                var c = ctx
                c.translateBy(x: p.x, y: p.y)
                c.rotate(by: .radians(p.angle))
                c.scaleBy(x: p.scale, y: p.scale)
                c.translateBy(x: -layout.advances[i] / 2, y: layout.xHeight / 2)
                c.opacity = p.opacity
                c.fill(glyph, with: .color(layout.accentMask[i] ? Self.accentColor : Self.ink))
            }
            // The head: an orange dot with a soft halo.
            let d = rope.head
            ctx.fill(Path(ellipseIn: CGRect(x: d.x - 9, y: d.y - 9, width: 18, height: 18)),
                     with: .color(Self.accentColor.opacity(0.16)))
            ctx.fill(Path(ellipseIn: CGRect(x: d.x - 3.5, y: d.y - 3.5, width: 7, height: 7)),
                     with: .color(Self.accentColor))
        }
    }

    private func chrome(size: CGSize, insets: EdgeInsets) -> some View {
        ZStack(alignment: .topLeading) {
            VStack(spacing: 12) {
                HStack {
                    Text(label)
                    Spacer()
                    Text(issue)
                }
                .font(.system(size: 11, weight: .medium, design: .monospaced))
                .tracking(1.6)
                .foregroundStyle(Self.ink.opacity(0.6))
                Rectangle()
                    .fill(Self.ink.opacity(0.18))
                    .frame(height: 0.5)
            }
            .padding(.horizontal, 24)
            .frame(width: size.width)
            .offset(y: insets.top + 16)

            HStack(spacing: 10) {
                Circle().fill(Self.accentColor).frame(width: 6, height: 6)
                Text(hint)
                    .font(.system(size: 12, weight: .medium, design: .monospaced))
                    .tracking(0.4)
                    .foregroundStyle(Self.ink.opacity(0.6))
            }
            .frame(width: size.width)
            .offset(y: size.height - max(insets.bottom, 20) - 32)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    // MARK: Touch

    private func drag(layout: RopeTextTrailLayout) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date.now
                if !dragging {
                    resume?.cancel()
                    let current = placements(layout: layout, now: now, autoStart: autoStart, dragging: false)
                    autoStart = nil
                    model.grab(rope: current, layout: layout, finger: value.location, now: now)
                    dragging = true
                    grabHaptic.impactOccurred()
                }
                model.finger = value.location
            }
            .onEnded { _ in
                let now = Date.now
                let current = model.touchRope(layout: layout, now: now)
                model.release(rope: current, now: now)
                dragging = false
                releaseHaptic.impactOccurred(intensity: 0.6)
                if reduceMotion {
                    fading = true
                    Task { @MainActor in
                        try? await Task.sleep(for: .seconds(0.4))
                        fading = false
                    }
                }
                scheduleResume()
            }
    }

    private func scheduleResume() {
        resume?.cancel()
        guard autoPlay, !reduceMotion else { return }
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            model.relaxStart = nil
            autoStart = .now
        }
    }
}

// MARK: - Rope

struct RopeTextTrailPlacement {
    var x: Double
    var y: Double
    var angle: Double
    var scale: Double = 1
    var opacity: Double = 1
}

struct RopeTextTrailRope {
    var letters: [RopeTextTrailPlacement]
    var head: CGPoint
}

/// A polyline measured by arc length; points along it are sampled with a smoothed tangent.
struct RopeTextTrailPolyline {
    var points: [CGPoint]
    var lengths: [Double]

    init(_ points: [CGPoint]) {
        self.points = points
        var l = [0.0]
        l.reserveCapacity(points.count)
        for i in 1..<max(points.count, 1) {
            l.append(l[i - 1] + hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y))
        }
        lengths = l
    }

    var length: Double { lengths.last ?? 0 }

    func point(at s: Double) -> CGPoint {
        guard points.count > 1 else { return points.first ?? .zero }
        let s = min(max(s, 0), length)
        var lo = 0, hi = lengths.count - 1
        while hi - lo > 1 {
            let mid = (lo + hi) / 2
            if lengths[mid] <= s { lo = mid } else { hi = mid }
        }
        let span = lengths[hi] - lengths[lo]
        let t = span > 0 ? (s - lengths[lo]) / span : 0
        let a = points[lo], b = points[hi]
        return CGPoint(x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t)
    }

    /// The point at arc length `s` and the tangent's angle, taken over ±4 pt so corners stay smooth.
    func sample(_ s: Double) -> (CGPoint, Double) {
        let p = point(at: s)
        let a = point(at: s - 4), b = point(at: s + 4)
        return (p, atan2(b.y - a.y, b.x - a.x))
    }
}

// MARK: - Layout

struct RopeTextTrailLayout {
    static let dotGap = 9.0
    static let margin = 20.0

    let chars: [Character]
    let accentMask: [Bool]
    let advances: [Double]
    /// Each letter's centre, measured from the start of the sentence.
    let centers: [Double]
    let textLength: Double
    let xHeight: Double
    let glyphs: [Path?]
    let size: CGSize
    let x0: Double
    let x1: Double
    let cy: Double
    let amp: Double
    /// Arc length on the resting curve where the sentence starts, and where the head rests.
    let tailStart: Double
    let headRest: Double
    let rest: RopeTextTrailPolyline
    /// The autoplay path: a lead-in, the resting curve up to the head, the drawn loops, the resting curve again.
    let master: RopeTextTrailPolyline
    let masterStart: Double
    let masterEnd: Double

    init(text: String, accent: String, size: CGSize) {
        let regular = RopeTextTrailFont.ctFont(italic: false, size: RopeTextTrail.fontSize)
        let italic = RopeTextTrailFont.ctFont(italic: true, size: RopeTextTrail.fontSize)
        chars = Array(text)
        var mask = [Bool](repeating: false, count: chars.count)
        if let r = text.range(of: accent) {
            let from = text.distance(from: text.startIndex, to: r.lowerBound)
            for i in from..<(from + accent.count) { mask[i] = true }
        }
        accentMask = mask
        var adv: [Double] = []
        var paths: [Path?] = []
        for (i, ch) in chars.enumerated() {
            let font = mask[i] ? italic : regular
            let (a, p) = RopeTextTrailFont.glyph(ch, font: font)
            adv.append(a)
            paths.append(p)
        }
        advances = adv
        var cs: [Double] = []
        var s = 0.0
        for a in adv {
            cs.append(s + a / 2)
            s += a
        }
        centers = cs
        textLength = s
        xHeight = Double(CTFontGetXHeight(regular))
        glyphs = paths
        self.size = size

        x0 = Self.margin
        x1 = Double(size.width) - Self.margin
        cy = Double(size.height) * 0.5
        let ropeLength = s + Self.dotGap + 4
        // Solve the amplitude so the curve is the rope's length plus 20 pt.
        let target = ropeLength + 20
        var lo = 0.0, hi = 160.0
        for _ in 0..<40 {
            let mid = (lo + hi) / 2
            if Self.curveLength(x0: x0, x1: x1, amp: mid) < target { lo = mid } else { hi = mid }
        }
        amp = (lo + hi) / 2
        rest = RopeTextTrailPolyline(Self.curve(x0: x0, x1: x1, cy: cy, amp: amp, sway: 0, count: 200))
        tailStart = (rest.length - ropeLength) / 2
        headRest = tailStart + s + Self.dotGap

        // The autoplay path.
        let w = Double(size.width), h = Double(size.height)
        var pts: [CGPoint] = []
        let start = rest.points[0]
        let (_, a0) = rest.sample(0)
        for k in stride(from: 240.0, to: 0, by: -20) {
            pts.append(CGPoint(x: start.x - cos(a0) * k, y: start.y - sin(a0) * k))
        }
        for p in rest.points where Self.arc(of: p, on: rest) < headRest { pts.append(p) }
        pts.append(rest.point(at: headRest))
        let leadIn = RopeTextTrailPolyline(pts).length
        let controls: [CGPoint] = [
            rest.point(at: headRest - 40), rest.point(at: headRest),
            CGPoint(x: 0.89 * w, y: 0.375 * h), CGPoint(x: 0.78 * w, y: 0.235 * h),
            CGPoint(x: 0.56 * w, y: 0.18 * h), CGPoint(x: 0.34 * w, y: 0.22 * h),
            CGPoint(x: 0.22 * w, y: 0.33 * h), CGPoint(x: 0.30 * w, y: 0.45 * h),
            CGPoint(x: 0.55 * w, y: 0.535 * h), CGPoint(x: 0.76 * w, y: 0.63 * h),
            CGPoint(x: 0.78 * w, y: 0.76 * h), CGPoint(x: 0.62 * w, y: 0.835 * h),
            CGPoint(x: 0.38 * w, y: 0.82 * h), CGPoint(x: 0.19 * w, y: 0.73 * h),
            CGPoint(x: 0.11 * w, y: 0.615 * h),
            rest.point(at: 0), rest.point(at: 40),
        ]
        for seg in 1..<(controls.count - 2) {
            let p0 = controls[seg - 1], p1 = controls[seg], p2 = controls[seg + 1], p3 = controls[seg + 2]
            for k in 1...24 {
                pts.append(Self.catmullRom(p0, p1, p2, p3, Double(k) / 24))
            }
        }
        // The drawn part ends on the resting curve's first point, so from there the arc runs as on the curve.
        let drawn = RopeTextTrailPolyline(pts).length
        for p in rest.points.dropFirst() where Self.arc(of: p, on: rest) < headRest + 60 { pts.append(p) }
        master = RopeTextTrailPolyline(pts)
        masterStart = leadIn
        masterEnd = drawn + headRest
    }

    // MARK: Curves

    static func curve(x0: Double, x1: Double, cy: Double, amp: Double, sway: Double, count: Int) -> [CGPoint] {
        (0...count).map { i in
            let u = Double(i) / Double(count)
            return CGPoint(x: x0 + (x1 - x0) * u, y: cy - amp * sin(2 * .pi * u) + 9 * sway * sin(.pi * u))
        }
    }

    static func curveLength(x0: Double, x1: Double, amp: Double) -> Double {
        RopeTextTrailPolyline(curve(x0: x0, x1: x1, cy: 0, amp: amp, sway: 0, count: 400)).length
    }

    /// The sway's amount over its window, φ = 0 … 1: a sag and a lift that start and end still.
    static func sway(_ phi: Double) -> Double {
        sin(2 * .pi * phi) * sin(.pi * phi) / 0.77
    }

    static func arc(of p: CGPoint, on line: RopeTextTrailPolyline) -> Double {
        // The resting curve's points are evenly spaced in x, so a point's index gives its arc length.
        let u = (p.x - line.points[0].x) / (line.points[line.points.count - 1].x - line.points[0].x)
        let i = Int((u * Double(line.points.count - 1)).rounded())
        return line.lengths[min(max(i, 0), line.lengths.count - 1)]
    }

    static func catmullRom(_ p0: CGPoint, _ p1: CGPoint, _ p2: CGPoint, _ p3: CGPoint, _ t: Double) -> CGPoint {
        let t2 = t * t, t3 = t2 * t
        func f(_ a: Double, _ b: Double, _ c: Double, _ d: Double) -> Double {
            0.5 * ((2 * b) + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3)
        }
        return CGPoint(x: f(p0.x, p1.x, p2.x, p3.x), y: f(p0.y, p1.y, p2.y, p3.y))
    }

    static func easeInOutSine(_ t: Double) -> Double { -(cos(.pi * t) - 1) / 2 }

    // MARK: Ropes

    /// The rope lying on the resting curve, swayed by `sway` (−1 … 1).
    func restRope(sway: Double) -> RopeTextTrailRope {
        let line = sway == 0 ? rest : RopeTextTrailPolyline(Self.curve(x0: x0, x1: x1, cy: cy, amp: amp, sway: sway, count: 120))
        var letters: [RopeTextTrailPlacement] = []
        letters.reserveCapacity(chars.count)
        for c in centers {
            let (p, a) = line.sample(tailStart + c)
            letters.append(RopeTextTrailPlacement(x: p.x, y: p.y, angle: a))
        }
        return RopeTextTrailRope(letters: letters, head: line.point(at: headRest))
    }

    /// The rope at `e` seconds into the autoplay loop.
    func autoRope(e: Double) -> RopeTextTrailRope {
        let a = RopeTextTrail.moveStart, b = RopeTextTrail.moveEnd
        if e < a || e >= b {
            let phi = (e >= b ? e - b : e + RopeTextTrail.loop - b) / (RopeTextTrail.loop - (b - a))
            return restRope(sway: Self.sway(phi))
        }
        let tau = (e - a) / (b - a)
        let head = masterStart + Self.easeInOutSine(tau) * (masterEnd - masterStart)
        let speed = (masterEnd - masterStart) * .pi / 2 * sin(.pi * tau) / (b - a)
        return ropeOn(master, head: head, motion: min(1, speed / 700))
    }

    /// The rope along a drawn line whose head is at arc length `head`, moving with `motion` (0 … 1).
    func ropeOn(_ line: RopeTextTrailPolyline, head: Double, motion m: Double) -> RopeTextTrailRope {
        let stretch = 1 + 0.10 * m
        var letters: [RopeTextTrailPlacement] = []
        letters.reserveCapacity(chars.count)
        let n = Double(max(chars.count - 1, 1))
        for (i, c) in centers.enumerated() {
            let (p, a) = line.sample(head - Self.dotGap - (textLength - c) * stretch)
            let tail = 1 - Double(i) / n
            letters.append(RopeTextTrailPlacement(x: p.x, y: p.y, angle: a, scale: 1 - 0.18 * m * tail, opacity: 1 - 0.40 * m * tail))
        }
        return RopeTextTrailRope(letters: letters, head: line.point(at: head))
    }
}

// MARK: - Touch model

/// What a drag needs between frames: the line the head has drawn and the release snapshot.
final class RopeTextTrailModel {
    static let relaxSpan = 1.2
    static let follow = 0.14

    var touching = false
    var trail: [CGPoint] = []
    var finger: CGPoint = .zero
    var grabOffset: CGVector = .zero
    var grabTime = Date.distantPast
    var lastTime = Date.distantPast
    var speed = 0.0
    var relaxStart: Date? = nil
    var relaxFrom: RopeTextTrailRope? = nil
    var restSince = Date.now
    var fadeStart = Date.distantPast

    func grab(rope: RopeTextTrailRope, layout: RopeTextTrailLayout, finger: CGPoint, now: Date) {
        touching = true
        relaxStart = nil
        relaxFrom = nil
        // The line so far: behind the tail along its direction, through the letters, to the head.
        var pts: [CGPoint] = []
        if let first = rope.letters.first {
            let back = first.angle + .pi
            for k in stride(from: 300.0, to: 0, by: -20) {
                pts.append(CGPoint(x: first.x + cos(back) * k, y: first.y + sin(back) * k))
            }
        }
        for p in rope.letters { pts.append(CGPoint(x: p.x, y: p.y)) }
        pts.append(rope.head)
        trail = pts
        self.finger = finger
        grabOffset = CGVector(dx: finger.x - rope.head.x, dy: finger.y - rope.head.y)
        grabTime = now
        lastTime = now
        speed = 0
    }

    /// The rope while a finger holds it; the head glides onto the finger and the line grows behind it.
    func touchRope(layout: RopeTextTrailLayout, now: Date) -> RopeTextTrailRope {
        if trail.isEmpty { return layout.restRope(sway: 0) }
        let k = exp(-max(0, now.timeIntervalSince(grabTime)) / Self.follow)
        let head = CGPoint(x: finger.x - grabOffset.dx * k, y: finger.y - grabOffset.dy * k)
        let last = trail[trail.count - 1]
        let dist = hypot(head.x - last.x, head.y - last.y)
        let dt = now.timeIntervalSince(lastTime)
        if dt > 0 {
            let v = dist / dt
            speed += (v - speed) * min(1, dt / 0.12)
            lastTime = now
        }
        if dist > 0.75 { trail.append(head) }
        // Keep only what the rope can reach.
        let keep = layout.textLength * 1.3 + 400
        var line = RopeTextTrailPolyline(trail)
        if line.length > keep * 1.5 {
            var drop = 0
            while drop < trail.count - 2 && line.length - line.lengths[drop] > keep { drop += 1 }
            trail.removeFirst(drop)
            line = RopeTextTrailPolyline(trail)
        }
        return layout.ropeOn(line, head: line.length, motion: min(1, speed / 700))
    }

    func release(rope: RopeTextTrailRope, now: Date) {
        touching = false
        relaxFrom = rope
        relaxStart = now
        restSince = now.addingTimeInterval(Self.relaxSpan)
        fadeStart = now
    }

    /// The rope springing back from the release snapshot to `rest`, the head first.
    func relaxRope(to rest: RopeTextTrailRope, elapsed: Double) -> RopeTextTrailRope {
        guard let from = relaxFrom else { return rest }
        let n = Double(max(rest.letters.count - 1, 1))
        var letters: [RopeTextTrailPlacement] = []
        letters.reserveCapacity(rest.letters.count)
        for (i, r) in rest.letters.enumerated() {
            let f = from.letters[i]
            let delay = 0.3 * (1 - Double(i) / n)
            let p = Self.spring((elapsed - delay) / 0.9)
            var da = r.angle - f.angle
            da = atan2(sin(da), cos(da))
            letters.append(RopeTextTrailPlacement(
                x: f.x + (r.x - f.x) * p, y: f.y + (r.y - f.y) * p, angle: f.angle + da * p,
                scale: f.scale + (1 - f.scale) * min(1, p), opacity: f.opacity + (1 - f.opacity) * min(1, p)))
        }
        let ph = Self.spring(elapsed / 0.9)
        let head = CGPoint(x: from.head.x + (rest.head.x - from.head.x) * ph, y: from.head.y + (rest.head.y - from.head.y) * ph)
        return RopeTextTrailRope(letters: letters, head: head)
    }

    /// Under reduced motion the rope fades back onto the curve after a release.
    func fadeIn(now: Date, reduced: Bool) -> Double {
        guard reduced, !touching else { return 1 }
        let t = now.timeIntervalSince(fadeStart)
        return t < 0.35 ? max(0, t / 0.35) : 1
    }

    /// A damped spring from 0 to 1 over x = 0 … 1, with a 4 % overshoot; exactly 0 before and 1 after.
    static func spring(_ x: Double) -> Double {
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        return 1 - exp(-6 * x) * cos(5.65 * x)
    }
}

// MARK: - Grain

/// Fine paper grain: a fixed scatter of tiny ink dots, drawn once.
struct RopeTextTrailGrain: View {
    var body: some View {
        Canvas { ctx, size in
            var seed: UInt64 = 0x9E37_79B9
            func next() -> Double {
                seed = seed &* 6_364_136_223_846_793_005 &+ 1_442_695_040_888_963_407
                return Double(seed >> 33) / Double(1 << 31)
            }
            let count = Int(size.width * size.height / 110)
            for _ in 0..<count {
                let x = next() * size.width, y = next() * size.height
                let r = 0.35 + next() * 0.55
                let a = 0.04 + next() * 0.07
                ctx.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r)),
                         with: .color(RopeTextTrail.ink.opacity(a)))
            }
        }
        .allowsHitTesting(false)
    }
}

// MARK: - Font

enum RopeTextTrailFont {
    static let registered: Void = {
        for name in ["RopeTextTrail-InstrumentSerif-Regular", "RopeTextTrail-InstrumentSerif-Italic"] {
            if let url = Bundle.main.url(forResource: name, withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()

    static func ctFont(italic: Bool, size: Double) -> CTFont {
        _ = registered
        return CTFontCreateWithName((italic ? "InstrumentSerif-Italic" : "InstrumentSerif-Regular") as CFString, size, nil)
    }

    /// A character's advance and its outline, with the origin on the baseline at the left (y down).
    static func glyph(_ ch: Character, font: CTFont) -> (Double, Path?) {
        var chars = Array(String(ch).utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(font, &chars, &glyphs, chars.count)
        var adv = CGSize.zero
        CTFontGetAdvancesForGlyphs(font, .horizontal, &glyphs, &adv, 1)
        var flip = CGAffineTransform(scaleX: 1, y: -1)
        guard ch != " ", let cg = CTFontCreatePathForGlyph(font, glyphs[0], &flip) else { return (Double(adv.width), nil) }
        return (Double(adv.width), Path(cg))
    }
}
