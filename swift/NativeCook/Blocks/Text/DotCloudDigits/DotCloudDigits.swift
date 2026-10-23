import CoreText
import SwiftUI
import UIKit

/*
 Dot Cloud Digits — a month's numbers, each drawn by the letters of its own name.

 A big number ("29") is outlined by 150 small capitals that spell it ("TWENTYNINE…") along the
 glyph's contours, on a black field of faint letters that twinkle. Changing the number scatters
 the letters into a slow, swirling cloud below the figure and gathers them into the next outline,
 where they now spell the new name.

 autoPlay timeline (loop = 9 s, three numbers of 3 s each, k = 0, 1, 2 starting at 3k s):
   3k + 0.0 … 3k + 1.2  number k rests, fully formed (t = 0 is the screen's first look).
   3k + 1.2 … 3k + 3.0  transition k → k+1 (1.8 s). Each letter waits 0…0.3 s (seeded), then
                        travels 1.5 s along a curve through its cloud point (ease-in-out cubic);
                        the cloud turns 0.55 rad as it goes. Halfway (in the cloud) the letter
                        becomes the new name's letter; it shrinks to 70 % and dims to 50 % there.
                        Caption: the old one fades out over the first 35 %, the new one fades in
                        over the last 40 %. The page bar slides with the transition.
   9.0                  back at number 0, formed: identical to t = 0.
 Background letters twinkle with whole-number cycles per loop, and a glint (opacity 0.8 → 1) runs
 along the outline's letters in index order once every 3 s, so the period is seamless.

 Touch: a drag pushes the outline's letters away from the finger (radius 90 pt, up to 46 pt);
 on release they spring back (e^{-6t}·cos 11t). A tap moves to the next number. 2 s after the
 last touch the screen returns to the first number and the loop starts again.
 Reduced motion: no loop, no cloud, no twinkle; a tap cross-fades to the next number (0.4 s).
 */

struct DotCloudDigitsStat: Equatable {
    /// The figure that is drawn.
    var value: String
    /// Its name, written under the caption; its letters (without the hyphen) draw the figure.
    var word: String
    var caption: String

    static let samples = [
        DotCloudDigitsStat(value: "29", word: "TWENTY-NINE", caption: "runs before 8 am"),
        DotCloudDigitsStat(value: "12", word: "TWELVE", caption: "new routes mapped"),
        DotCloudDigitsStat(value: "64", word: "SIXTY-FOUR", caption: "kilometres on trails"),
    ]
}

struct DotCloudDigits: View {
    var stats = DotCloudDigitsStat.samples
    var title = "OCTOBER IN NUMBERS"
    var autoPlay = false

    static let loop: Double = 9
    static let slot: Double = 3
    static let hold: Double = 1.2
    static let transition: Double = 1.8
    static let maxDelay: Double = 0.3
    static let particles = 150
    static let stars = 56

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// When set, the autoPlay loop's t = 0 (it may lie in the future while a return plays).
    @State private var autoStart: Date? = nil
    /// The manual state: number `to`, reached from `from` by a transition that started at `since`.
    @State private var from = 0
    @State private var to = 0
    @State private var since: Date? = nil
    @State private var finger: CGPoint? = nil
    @State private var pressedAt: Date? = nil
    @State private var releasedAt: Date? = nil
    @State private var dragging = false
    @State private var resume: Task<Void, Never>? = nil
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)
    @State private var shapes: DotCloudDigitsShapes? = nil
    @State private var shapesSize: CGSize = .zero

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            let layout = DotCloudDigitsShapes.layout(stats: stats, size: size)
            TimelineView(.animation(paused: reduceMotion && since == nil)) { timeline in
                let now = timeline.date
                let state = state(at: now)
                ZStack(alignment: .topLeading) {
                    Color(red: 0.016, green: 0.016, blue: 0.02)
                    if let shapes, shapesSize == size {
                        canvas(shapes: shapes, state: state, now: now, size: size)
                    }
                    chrome(state: state, size: size, insets: insets, layout: layout)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .contentShape(Rectangle())
            .gesture(drag)
            .ignoresSafeArea()
            .onAppear { build(size: size) }
            .onChange(of: size) { _, new in build(size: new) }
        }
        .onAppear {
            haptic.prepare()
            if autoPlay && !reduceMotion { autoStart = .now }
        }
        .onDisappear { resume?.cancel() }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityText)
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { advance(at: .now) }
    }

    private var accessibilityText: String {
        let s = stats[to]
        return "\(title.capitalized): \(s.value) \(s.caption)"
    }

    // MARK: State

    /// What is shown at a moment: number `a` turning into `b`, `tau` seconds into the transition (nil: resting on `b`).
    struct Shown {
        var a: Int
        var b: Int
        var tau: Double?
        /// Reduced motion: the cross-fade from `a` to `b`, 0…1.
        var fade: Double?
    }

    private func state(at date: Date) -> Shown {
        if let autoStart, date >= autoStart, !reduceMotion {
            let e = date.timeIntervalSince(autoStart).truncatingRemainder(dividingBy: Self.loop)
            let k = min(stats.count - 1, Int(e / Self.slot))
            let within = e - Double(k) * Self.slot
            if within < Self.hold { return Shown(a: k, b: k, tau: nil) }
            return Shown(a: k, b: (k + 1) % stats.count, tau: within - Self.hold)
        }
        if let since {
            let tau = date.timeIntervalSince(since)
            if reduceMotion {
                let f = min(1, max(0, tau / 0.4))
                return Shown(a: from, b: to, tau: nil, fade: f < 1 ? f : nil)
            }
            if tau < Self.transition { return Shown(a: from, b: to, tau: max(0, tau)) }
        }
        return Shown(a: to, b: to, tau: nil)
    }

    /// Takes the loop's current state over as the manual one.
    private func takeOver(at date: Date) {
        resume?.cancel()
        guard autoStart != nil else { return }
        let s = state(at: date)
        from = s.a
        to = s.b
        since = s.tau.map { date.addingTimeInterval(-$0) }
        autoStart = nil
    }

    private func advance(at date: Date) {
        takeOver(at: date)
        if let since, date.timeIntervalSince(since) < (reduceMotion ? 0.4 : Self.transition) {
            scheduleResume()
            return
        }
        from = to
        to = (to + 1) % stats.count
        since = date
        haptic.impactOccurred(intensity: 0.7)
        if reduceMotion {
            // The cross-fade is over after 0.4 s; clearing it lets the timeline pause.
            Task { @MainActor in
                try? await Task.sleep(for: .seconds(0.45))
                if self.since == date { self.since = nil }
            }
        }
        scheduleResume()
    }

    private func scheduleResume() {
        resume?.cancel()
        guard autoPlay, !reduceMotion else { return }
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            var now = Date.now
            if let since, now.timeIntervalSince(since) < Self.transition {
                let left = Self.transition - now.timeIntervalSince(since)
                try? await Task.sleep(for: .seconds(left))
                guard !Task.isCancelled else { return }
                now = .now
            }
            if to != 0 {
                from = to
                to = 0
                since = now
                autoStart = now.addingTimeInterval(Self.transition)
            } else {
                autoStart = now
            }
        }
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date.now
                if !dragging {
                    dragging = true
                    takeOver(at: now)
                    finger = value.startLocation
                }
                if !reduceMotion, hypot(value.translation.width, value.translation.height) > 6 {
                    if pressedAt == nil || releasedAt != nil {
                        pressedAt = now
                        releasedAt = nil
                        haptic.impactOccurred(intensity: 0.5)
                    }
                    finger = value.location
                }
            }
            .onEnded { value in
                dragging = false
                let moved = hypot(value.translation.width, value.translation.height) > 6
                if moved, pressedAt != nil {
                    releasedAt = .now
                    scheduleResume()
                } else {
                    advance(at: .now)
                }
            }
    }

    /// How hard the finger pushes, 0…1, springing back after release.
    private func push(at date: Date) -> Double {
        guard let pressedAt else { return 0 }
        let inT = date.timeIntervalSince(pressedAt)
        let held = 1 - exp(-12 * max(0, inT))
        guard let releasedAt else { return held }
        let t = date.timeIntervalSince(releasedAt)
        if t > 1.2 { return 0 }
        return held * exp(-6 * t) * cos(11 * t)
    }

    private func build(size: CGSize) {
        guard size.width > 0, size != shapesSize else { return }
        shapes = DotCloudDigitsShapes(stats: stats, size: size)
        shapesSize = size
    }

    // MARK: Drawing

    private func canvas(shapes: DotCloudDigitsShapes, state: Shown, now: Date, size: CGSize) -> some View {
        let loopT = autoStart.map { now.timeIntervalSince($0) } ?? now.timeIntervalSinceReferenceDate
        let strength = reduceMotion ? 0 : push(at: now)
        let finger = self.finger
        let reduce = reduceMotion
        return Canvas { ctx, _ in
            var symbols: [Character: GraphicsContext.ResolvedSymbol] = [:]
            for ch in shapes.letters { if let s = ctx.resolveSymbol(id: String(ch)) { symbols[ch] = s } }
            let words = shapes.words

            // Background letters.
            let starFlip = state.tau.map { DotCloudDigitsMath.ease(min(1, $0 / 0.9)) } ?? 0
            for (i, star) in shapes.stars.enumerated() {
                let twinkle = reduce ? 0.6 : 0.5 + 0.5 * sin(2 * .pi * (Double(star.cycles) * loopT / Self.loop) + star.phase)
                let swap = state.tau == nil ? 0 : sin(.pi * starFlip)
                let word = starFlip < 0.5 ? words[state.a] : words[state.b]
                let ch = word[i % word.count]
                guard let sym = symbols[ch] else { continue }
                var c = ctx
                c.opacity = (star.base + 0.32 * twinkle) * (1 - 0.8 * swap)
                c.translateBy(x: star.point.x, y: star.point.y)
                c.scaleBy(x: star.scale, y: star.scale)
                c.draw(sym, at: .zero)
            }

            // The figure.
            let a = shapes.targets[state.a], b = shapes.targets[state.b]
            let fadeIn = state.fade
            for i in 0..<Self.particles {
                var p: CGPoint
                var s = 0.0
                if let tau = state.tau {
                    let d = shapes.delay[i]
                    s = DotCloudDigitsMath.easeInOut(min(1, max(0, (tau - d) / (Self.transition - Self.maxDelay))))
                    p = DotCloudDigitsMath.travel(from: a[i], to: b[i], cloud: shapes.cloud[i], center: shapes.cloudCenter, s: s)
                } else {
                    p = b[i]
                }
                if strength != 0, let f = finger {
                    let dx = p.x - f.x, dy = p.y - f.y
                    let dist = max(1, hypot(dx, dy))
                    if dist < 90 {
                        let k = pow(1 - dist / 90, 2) * 46 * strength
                        p.x += dx / dist * k
                        p.y += dy / dist * k
                    }
                }
                let bump = sin(.pi * s)
                let scale = 1 - 0.3 * bump
                // A glint runs along the outline three times a loop (index order), so a resting figure still shimmers.
                let glint = reduce ? 1 : 0.8 + 0.2 * pow(max(0, cos(2 * .pi * (Double(i) / Double(Self.particles) - loopT / Self.slot))), 6)
                let opacity = (1 - 0.5 * bump) * glint
                if let fade = fadeIn {
                    for (word, at, o) in [(words[state.a], a[i], 1 - fade), (words[state.b], b[i], fade)] {
                        guard let sym = symbols[word[i % word.count]] else { continue }
                        var c = ctx
                        c.opacity = o
                        c.draw(sym, at: at)
                    }
                    continue
                }
                let word = s < 0.5 ? words[state.a] : words[state.b]
                guard let sym = symbols[word[i % word.count]] else { continue }
                var c = ctx
                c.opacity = opacity
                c.translateBy(x: p.x, y: p.y)
                c.scaleBy(x: scale, y: scale)
                c.draw(sym, at: .zero)
            }
        } symbols: {
            ForEach(shapes.letters.map(String.init), id: \.self) { ch in
                Text(ch)
                    .font(DotCloudDigitsFont.font(size: DotCloudDigitsShapes.letterSize))
                    .foregroundStyle(Color(red: 0.96, green: 0.95, blue: 0.93))
                    .tag(ch)
            }
        }
        .frame(width: size.width, height: size.height)
        .allowsHitTesting(false)
    }

    private func chrome(state: Shown, size: CGSize, insets: EdgeInsets, layout: DotCloudDigitsShapes.Layout) -> some View {
        // Caption cross-fade: out over the first 35 % of a transition, in over the last 40 %.
        let p = state.tau.map { min(1, $0 / Self.transition) } ?? (state.fade ?? 1)
        let outT = state.tau != nil ? DotCloudDigitsMath.ease(min(1, p / 0.35)) : (state.fade ?? 1)
        let inT = state.tau != nil ? DotCloudDigitsMath.ease(max(0, (p - 0.6) / 0.4)) : (state.fade ?? 1)
        let resting = state.tau == nil && state.fade == nil
        let ink = Color(red: 0.96, green: 0.95, blue: 0.93)
        let bar = DotCloudDigitsMath.easeInOut(p)

        return ZStack(alignment: .topLeading) {
            HStack {
                Text(title)
                Spacer()
                ZStack(alignment: .trailing) {
                    counter(state.a).opacity(resting ? 0 : 1 - outT)
                    counter(state.b).opacity(resting ? 1 : inT)
                }
            }
            .font(DotCloudDigitsFont.font(size: 12))
            .tracking(2)
            .foregroundStyle(ink.opacity(0.55))
            .padding(.horizontal, 24)
            .frame(width: size.width)
            .offset(y: insets.top + 20)

            ZStack {
                if !resting { caption(stats[state.a]).opacity(1 - outT).offset(y: -6 * outT) }
                caption(stats[state.b]).opacity(resting ? 1 : inT).offset(y: resting ? 0 : 8 * (1 - inT))
            }
            .frame(width: size.width - 48)
            .offset(x: 24, y: layout.captionTop)

            HStack(spacing: 6) {
                ForEach(stats.indices, id: \.self) { i in
                    let on = resting ? (i == state.b ? 1.0 : 0) : (i == state.a ? 1 - bar : i == state.b ? bar : 0)
                    Capsule()
                        .fill(ink.opacity(0.25 + 0.65 * on))
                        .frame(width: 8 + 20 * on, height: 3)
                }
            }
            .frame(width: size.width)
            .offset(y: size.height - max(insets.bottom, 20) - 28)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    private func counter(_ k: Int) -> some View {
        Text(String(format: "%02d / %02d", k + 1, stats.count))
            .monospacedDigit()
    }

    private func caption(_ stat: DotCloudDigitsStat) -> some View {
        VStack(spacing: 10) {
            Text(stat.caption)
                .font(DotCloudDigitsFont.font(size: 22))
                .foregroundStyle(Color(red: 0.96, green: 0.95, blue: 0.93))
            Text(stat.word)
                .font(DotCloudDigitsFont.font(size: 11))
                .tracking(3)
                .foregroundStyle(Color(red: 0.96, green: 0.95, blue: 0.93).opacity(0.45))
        }
        .lineLimit(1)
        .minimumScaleFactor(0.7)
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Font

enum DotCloudDigitsFont {
    static let name = "Poppins-SemiBold"
    static let registered: Void = {
        if let url = Bundle.main.url(forResource: "DotCloudDigits-Poppins-SemiBold", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func font(size: CGFloat) -> Font {
        _ = registered
        return .custom(name, fixedSize: size)
    }

    static func ctFont(size: CGFloat) -> CTFont {
        _ = registered
        return CTFontCreateWithName(name as CFString, size, nil)
    }
}

// MARK: - Geometry

struct DotCloudDigitsStar {
    var point: CGPoint
    var scale: Double
    var base: Double
    var cycles: Int
    var phase: Double
}

/// Everything about the figures that depends only on the screen size: where each letter sits in
/// each number, its cloud point and delay, and the background letters.
struct DotCloudDigitsShapes {
    static let letterSize: CGFloat = 10.5
    /// Largest figure height; the figures share one scale so that "12" is not bigger than "64".
    static let maxHeight: CGFloat = 300

    struct Layout {
        var scale: CGFloat
        var centerY: CGFloat
        var captionTop: CGFloat
    }

    let targets: [[CGPoint]]
    let cloud: [CGPoint]
    let cloudCenter: CGPoint
    let delay: [Double]
    let stars: [DotCloudDigitsStar]
    let words: [[Character]]
    let letters: [Character]

    /// Shared numbers: the figures' scale (pt per em) and where the figure and caption sit.
    static func layout(stats: [DotCloudDigitsStat], size: CGSize) -> Layout {
        let unit = stats.map { DotCloudDigitsMath.outline(of: $0.value).bounds }
        let maxW = unit.map(\.width).max() ?? 1
        let maxH = unit.map(\.height).max() ?? 1
        let scale = min((size.width - 64) / maxW, maxHeight / maxH)
        let centerY = size.height * 0.45
        return Layout(scale: scale, centerY: centerY, captionTop: centerY + maxH * scale / 2 + 44)
    }

    init(stats: [DotCloudDigitsStat], size: CGSize) {
        let layout = Self.layout(stats: stats, size: size)
        let n = DotCloudDigits.particles
        targets = stats.map { stat in
            let outline = DotCloudDigitsMath.outline(of: stat.value)
            let b = outline.bounds
            let origin = CGPoint(x: size.width / 2 - b.midX * layout.scale, y: layout.centerY - b.midY * layout.scale)
            return DotCloudDigitsMath.sample(outline.contours, count: n).map {
                CGPoint(x: origin.x + $0.x * layout.scale, y: origin.y + $0.y * layout.scale)
            }
        }
        let center = CGPoint(x: size.width / 2, y: layout.centerY + 70)
        cloudCenter = center
        cloud = (0..<n).map { i in
            let r = DotCloudDigitsMath.hash(i, 1), a = DotCloudDigitsMath.hash(i, 2), y = DotCloudDigitsMath.hash(i, 3)
            let spread = 0.35 + 0.65 * sqrt(r)
            return CGPoint(x: center.x + (a * 2 - 1) * (size.width * 0.44) * spread,
                           y: center.y + (y * 2 - 1) * 48 * spread)
        }
        delay = (0..<n).map { DotCloudDigitsMath.hash($0, 4) * DotCloudDigits.maxDelay }
        // Background letters below the header and anywhere but on the caption's band, which they would make hard to read.
        let band = (layout.captionTop - 12)...(layout.captionTop + 64)
        stars = (0..<DotCloudDigits.stars).map { i in
            var y = 104 + DotCloudDigitsMath.hash(i, 6) * (size.height - 164)
            if band.contains(y) { y = y - band.lowerBound < band.upperBound - y ? band.lowerBound - 4 - (y - band.lowerBound) : band.upperBound + 4 + (band.upperBound - y) }
            return DotCloudDigitsStar(
                point: CGPoint(x: 12 + DotCloudDigitsMath.hash(i, 5) * (size.width - 24), y: y),
                scale: 0.8 + 0.25 * DotCloudDigitsMath.hash(i, 7),
                base: 0.12 + 0.18 * DotCloudDigitsMath.hash(i, 8),
                cycles: 1 + Int(DotCloudDigitsMath.hash(i, 9) * 3),
                phase: DotCloudDigitsMath.hash(i, 10) * 2 * .pi
            )
        }
        words = stats.map { Array($0.word.filter(\.isLetter)) }
        letters = Array(Set(words.flatMap { $0 })).sorted()
    }
}

enum DotCloudDigitsMath {
    /// A seeded number in 0..<1 (the same 32-bit integer hash in every stack).
    static func hash(_ i: Int, _ salt: Int) -> Double {
        var x = UInt32(truncatingIfNeeded: i &* 374_761_393 &+ salt &* 668_265_263)
        x = (x ^ (x >> 13)) &* 1_274_126_177
        x = x ^ (x >> 16)
        return Double(x) / 4_294_967_296
    }

    static func ease(_ t: Double) -> Double { 1 - pow(1 - t, 3) }

    static func easeInOut(_ t: Double) -> Double {
        t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
    }

    /// A curve from `a` to `b` that passes the cloud point halfway, the cloud turning as it goes.
    static func travel(from a: CGPoint, to b: CGPoint, cloud: CGPoint, center: CGPoint, s: Double) -> CGPoint {
        // Turn the cloud point about the cloud's centre in a squashed space, so the band swirls flat.
        let angle = 0.55 * s
        let dx = cloud.x - center.x, dy = (cloud.y - center.y) * 3
        let rx = dx * cos(angle) - dy * sin(angle), ry = dx * sin(angle) + dy * cos(angle)
        let c = CGPoint(x: center.x + rx, y: center.y + ry / 3)
        let k = CGPoint(x: 2 * c.x - (a.x + b.x) / 2, y: 2 * c.y - (a.y + b.y) / 2)
        let u = 1 - s
        return CGPoint(x: u * u * a.x + 2 * s * u * k.x + s * s * b.x,
                       y: u * u * a.y + 2 * s * u * k.y + s * s * b.y)
    }

    struct Outline {
        var contours: [[CGPoint]]
        var bounds: CGRect
    }

    /// The figure's outline at 1 pt per em, y down, from the glyphs placed by their advances (no kerning).
    static func outline(of text: String) -> Outline {
        let font = DotCloudDigitsFont.ctFont(size: 1)
        var chars = Array(text.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(font, &chars, &glyphs, chars.count)
        var advances = [CGSize](repeating: .zero, count: glyphs.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &advances, glyphs.count)
        var contours: [[CGPoint]] = []
        var x: CGFloat = 0
        for (g, adv) in zip(glyphs, advances) {
            if let path = CTFontCreatePathForGlyph(font, g, nil) {
                contours += flatten(path).map { $0.map { CGPoint(x: $0.x + x, y: -$0.y) } }
            }
            x += adv.width
        }
        let all = contours.flatMap { $0 }
        let minX = all.map(\.x).min() ?? 0, maxX = all.map(\.x).max() ?? 1
        let minY = all.map(\.y).min() ?? 0, maxY = all.map(\.y).max() ?? 1
        return Outline(contours: contours, bounds: CGRect(x: minX, y: minY, width: maxX - minX, height: maxY - minY))
    }

    /// Polylines of a path's contours (curves cut into 16 straight pieces), each closed back to its start.
    static func flatten(_ path: CGPath) -> [[CGPoint]] {
        var result: [[CGPoint]] = []
        var current: [CGPoint] = []
        var last = CGPoint.zero
        path.applyWithBlock { el in
            let e = el.pointee
            switch e.type {
            case .moveToPoint:
                if current.count > 1 { result.append(current) }
                current = [e.points[0]]
                last = e.points[0]
            case .addLineToPoint:
                current.append(e.points[0]); last = e.points[0]
            case .addQuadCurveToPoint:
                let c = e.points[0], p = e.points[1]
                for k in 1...16 {
                    let t = CGFloat(k) / 16, u = 1 - t
                    current.append(CGPoint(x: u * u * last.x + 2 * u * t * c.x + t * t * p.x,
                                           y: u * u * last.y + 2 * u * t * c.y + t * t * p.y))
                }
                last = p
            case .addCurveToPoint:
                let c1 = e.points[0], c2 = e.points[1], p = e.points[2]
                for k in 1...16 {
                    let t = CGFloat(k) / 16, u = 1 - t
                    let a = u * u * u, b = 3 * u * u * t, c = 3 * u * t * t, d = t * t * t
                    current.append(CGPoint(x: a * last.x + b * c1.x + c * c2.x + d * p.x,
                                           y: a * last.y + b * c1.y + c * c2.y + d * p.y))
                }
                last = p
            case .closeSubpath:
                if let first = current.first { current.append(first); last = first }
            @unknown default:
                break
            }
        }
        if current.count > 1 { result.append(current) }
        return result
    }

    /// `count` points spread evenly along all the contours: each gets its share of the total
    /// length (rounded; the longest takes the remainder), and its points sit in the middle of
    /// equal steps from its start.
    static func sample(_ contours: [[CGPoint]], count: Int) -> [CGPoint] {
        let lengths = contours.map(length)
        let total = lengths.reduce(0, +)
        var counts = lengths.map { Int(($0 / total * Double(count)).rounded()) }
        let diff = count - counts.reduce(0, +)
        if let longest = lengths.indices.max(by: { lengths[$0] < lengths[$1] }) { counts[longest] += diff }
        var points: [CGPoint] = []
        for (ci, c) in contours.enumerated() where counts[ci] > 0 {
            let step = lengths[ci] / Double(counts[ci])
            for k in 0..<counts[ci] { points.append(point(on: c, at: (Double(k) + 0.5) * step)) }
        }
        return points
    }

    static func length(_ c: [CGPoint]) -> Double {
        zip(c, c.dropFirst()).reduce(0.0) { $0 + hypot($1.1.x - $1.0.x, $1.1.y - $1.0.y) }
    }

    static func point(on c: [CGPoint], at distance: Double) -> CGPoint {
        var walked = 0.0
        for j in 0..<(c.count - 1) {
            let seg = hypot(c[j + 1].x - c[j].x, c[j + 1].y - c[j].y)
            if walked + seg >= distance, seg > 0 {
                let t = (distance - walked) / seg
                return CGPoint(x: c[j].x + (c[j + 1].x - c[j].x) * t, y: c[j].y + (c[j + 1].y - c[j].y) * t)
            }
            walked += seg
        }
        return c[c.count - 1]
    }
}
