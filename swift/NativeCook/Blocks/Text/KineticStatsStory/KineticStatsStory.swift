import CoreText
import SwiftUI
import UIKit

/*
 Kinetic Stats Story: a year-in-review story for a running app. Three cards on a drifting mesh
 gradient; the stats are set far bigger than the screen and travel through it.

 Layout (pt, W × H the whole screen, `top`/`bottom` the safe area): story bars at top + 8 (3 pt,
 12 pt in from the sides, 4 pt apart), a header row centred at top + 32 (label left, close button
 right), round buttons (36 pt) in the bottom corners, 16 pt from the sides and 12 pt above the safe
 area. All type is Inter Tight Bold.

 Timeline of the story (`KineticStatsStory.loop` = 10 s, story time s):
   0.00–1.55  Cover at rest: kicker "Your year on the road" and "Rewind" / "’26" (sized to fill
              W − 48) at the bottom left, on the deep teal mesh. The first bar fills.
   1.55–2.00  The cover's three lines leave to the left (48 pt, ease-in cubic, 0.35 s each, 0.05 s
              apart) while they blur (sigma 0 → 10) and fade; the mesh turns orange (1.5–2.4).
   1.85–2.41  "This year you ran" / "1,284 kilometres." (30 pt) enter from the right (48 pt,
              ease-out cubic, 0.5 s, 0.06 s apart), blur 10 → 0.
   2.15–5.20  The number "1,284" (0.7 W, so about twice the screen wide) comes in from the right
              edge (W − 8 pt, 0.75 s, ease-out cubic) and from 2.75 slides out to the left, slowly
              at first (its width + 0.3 W + 24 pt, 2.45 s, ease-in cubic). Its digits roll like an
              odometer from 2.2 to 3.4 (ease-out cubic), each slot turning ten more times than the
              one left of it; a digit rises out (fading) as the next rises in from 1.25 cap heights
              below, inside a band clipped to the digits.
   4.95–5.40  The caption leaves to the left like the cover; the mesh shifts (4.9–5.8).
   5.35–8.85  "And 212" / "sunrise" / "runs." (0.4 W, lines 0.94 em apart) come in from the right
              (0.95 W, ease-out cubic, 0.85 s, 0.1 s apart) and drift left, each at its own speed,
              so that by 8.85 its end sits at 0.62 W.
   8.80–9.30  The lines leave to the left (0.7 W, ease-in cubic, 0.4 s, 0.05 s apart); the bars
              drain (8.85–9.45, ease-in-out) and the mesh returns to teal (8.8–9.9).
   9.20–9.84  The cover's lines come back in from the right; from 9.84 the frame is the one at 0.
 The mesh's five blobs move on paths with a period of 10 s (or 5 s), so the loop has no seam.

 Touch: a tap on the left third goes to the previous card, elsewhere to the next card (light
 haptic); holding a finger down pauses the story, and it plays on from there on release. Without
 `autoPlay` the story waits on the cover and plays through once from a tap. Reduced motion: the
 story holds each card still (the number and the lines scaled to fit the width), taps cross-fade
 between cards in 0.3 s, and nothing plays by itself.
*/
struct KineticStatsStory: View {
    var label = "Rewind ’26"
    var kicker = "Your year on the road"
    var title = ["Rewind", "’26"]
    var caption = ["This year you ran", "1,284 kilometres."]
    /// The number of the first card; it rolls up to this value.
    var number = 1284
    var lines = ["And 212", "sunrise", "runs."]
    /// Plays the story by itself, over and over; one pass lasts `loop` seconds.
    var autoPlay = false
    var onClose: (() -> Void)? = nil

    static let loop = 10.0

    enum M {
        static let font = "InterTight-Bold"
        static let margin = 24.0
        static let shift = 48.0
        static let blur = 10.0
        /// Where each card starts when it is reached with a tap, and where its bar runs.
        static let cards = [0.0, 1.55, 4.95]
        static let bars: [(Double, Double)] = [(0, 1.55), (1.55, 4.95), (4.95, 8.85)]
        static let drain = (8.85, 0.6)
        static let tapTime = 0.3
        static let tapSlop = 10.0
        static let fade = 0.3
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The date story time 0 fell on.
    @State private var start = Date.now
    /// Story time, frozen while a finger is down.
    @State private var held: Double? = nil
    @State private var playing = false
    @State private var downAt = Date.now
    /// Reduced motion: the card shown, the one before it and when it changed.
    @State private var still = 0
    @State private var stillBefore = 0
    @State private var stillChange = Date.distantPast
    @State private var fading = false
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            TimelineView(.animation(paused: reduceMotion ? !fading : (held != nil || !playing))) { timeline in
                Canvas { context, size in
                    _ = Self.registered
                    draw(&context, size: size, top: insets.top, bottom: insets.bottom, date: timeline.date)
                }
            }
            .ignoresSafeArea()
            .contentShape(Rectangle())
            .gesture(touch(width: width))
            .accessibilityElement()
            .accessibilityLabel("\(kicker). \(title.joined(separator: " ")). \(caption.joined(separator: " ")) \(lines.joined(separator: " "))")
            .accessibilityHint("Tap the right side for the next card, the left side for the previous one.")
        }
        .background(Color(hex: 0x0d2433).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            start = .now
            playing = autoPlay
            haptic.prepare()
        }
        .task(id: start) { await finish() }
        .task(id: stillChange) {
            guard fading else { return }
            try? await Task.sleep(for: .seconds(M.fade))
            fading = false
        }
    }

    // MARK: Time and touch

    private func storyTime(at date: Date) -> Double {
        if let held { return held }
        guard playing else { return 0 }
        let raw = date.timeIntervalSince(start)
        return autoPlay ? raw.truncatingRemainder(dividingBy: Self.loop) : min(raw, Self.loop).truncatingRemainder(dividingBy: Self.loop)
    }

    /// Without autoPlay the story stops on the cover when a pass ends.
    private func finish() async {
        guard !autoPlay, playing else { return }
        let left = Self.loop - Date.now.timeIntervalSince(start)
        try? await Task.sleep(for: .seconds(max(left, 0)))
        guard !Task.isCancelled, held == nil else { return }
        playing = false
    }

    private func card(at s: Double) -> Int {
        s >= M.cards[2] && s < 9.2 ? 2 : s >= M.cards[1] && s < M.cards[2] ? 1 : 0
    }

    private func touch(width: Double) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .onChanged { _ in
                guard held == nil, !reduceMotion else { return }
                downAt = .now
                held = storyTime(at: .now)
            }
            .onEnded { value in
                let tap = Date.now.timeIntervalSince(downAt) < M.tapTime && hypot(value.translation.width, value.translation.height) < M.tapSlop
                let forward = value.location.x >= width / 3
                if reduceMotion {
                    stillBefore = still
                    still = forward ? (still + 1) % 3 : max(still - 1, 0)
                    stillChange = .now
                    fading = true
                    haptic.impactOccurred()
                    return
                }
                let now = held ?? 0
                held = nil
                if tap {
                    let current = card(at: now)
                    let target = forward ? (current == 2 ? 8.8 : M.cards[current + 1]) : M.cards[max(current - 1, 0)]
                    start = Date.now.addingTimeInterval(-target)
                    haptic.impactOccurred()
                } else {
                    start = Date.now.addingTimeInterval(-now)
                }
                playing = true
            }
    }

    // MARK: Drawing

    private func draw(_ context: inout GraphicsContext, size: CGSize, top: Double, bottom: Double, date: Date) {
        let frame = Frame(size: size, top: top, bottom: bottom)
        if reduceMotion {
            let k = min(max(date.timeIntervalSince(stillChange) / M.fade, 0), 1)
            if k < 1 {
                drawStill(&context, frame, card: stillBefore)
                var over = context
                over.opacity = k
                drawStill(&over, frame, card: still)
            } else {
                drawStill(&context, frame, card: still)
            }
            return
        }
        let s = storyTime(at: date)
        drawMesh(&context, frame, s: s, weights: Self.meshWeights(s))
        drawCover(context, frame, s: s)
        drawFirst(context, frame, s: s)
        drawSecond(context, frame, s: s)
        drawChrome(context, frame, s: s)
    }

    private func drawStill(_ context: inout GraphicsContext, _ frame: Frame, card: Int) {
        let weights = [[1.0, 0, 0], [0, 1.0, 0], [0, 0, 1.0]][card]
        drawMesh(&context, frame, s: [0, 3.5, 7][card], weights: weights)
        switch card {
        case 0: drawCover(context, frame, s: 0)
        case 1: drawFirst(context, frame, s: 3, fit: true)
        default: drawSecond(context, frame, s: 7, fit: true)
        }
        drawChrome(context, frame, s: [0.0, 4.95, 8.85][card])
    }

    struct Frame {
        let size: CGSize
        let top: Double
        let bottom: Double
        var w: Double { size.width }
        var h: Double { size.height }
    }

    /// How much of each card's palette the mesh shows.
    static func meshWeights(_ s: Double) -> [Double] {
        let c1 = smoothstep(1.5, 2.4, s) * (1 - smoothstep(4.9, 5.8, s))
        let c2 = smoothstep(4.9, 5.8, s) * (1 - smoothstep(8.8, 9.9, s))
        return [1 - c1 - c2, c1, c2]
    }

    private func drawMesh(_ context: inout GraphicsContext, _ frame: Frame, s: Double, weights: [Double]) {
        let w = frame.w, h = frame.h
        func mix(_ index: Int) -> Color {
            var rgb = [0.0, 0.0, 0.0]
            for (p, palette) in KineticStatsStoryMesh.palettes.enumerated() {
                let c = palette[index]
                rgb[0] += weights[p] * Double((c >> 16) & 0xff)
                rgb[1] += weights[p] * Double((c >> 8) & 0xff)
                rgb[2] += weights[p] * Double(c & 0xff)
            }
            return Color(.sRGB, red: rgb[0] / 255, green: rgb[1] / 255, blue: rgb[2] / 255)
        }
        context.fill(Path(CGRect(origin: .zero, size: frame.size)), with: .color(mix(0)))
        let reach = max(w, h)
        for (i, blob) in KineticStatsStoryMesh.blobs.enumerated() {
            let phase = tau * s / Self.loop
            let x = w * (blob.x + blob.ax * sin(blob.fx * phase + blob.px))
            let y = h * (blob.y + blob.ay * sin(blob.fy * phase + blob.py))
            let r = reach * blob.r
            let color = mix(i + 1)
            let gradient = Gradient(stops: [
                .init(color: color, location: 0),
                .init(color: color.opacity(0.6), location: 0.45),
                .init(color: color.opacity(0), location: 1),
            ])
            context.fill(
                Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r)),
                with: .radialGradient(gradient, center: CGPoint(x: x, y: y), startRadius: 0, endRadius: r)
            )
        }
        // A light scrim under the story bars and the header.
        context.fill(
            Path(CGRect(x: 0, y: 0, width: w, height: frame.top + 90)),
            with: .linearGradient(
                Gradient(colors: [.black.opacity(0.22), .black.opacity(0)]),
                startPoint: .zero, endPoint: CGPoint(x: 0, y: frame.top + 90)
            )
        )
    }

    // MARK: Cards

    private func drawCover(_ context: GraphicsContext, _ frame: Frame, s: Double) {
        let size = Self.fit(context, title.max(by: { $0.count < $1.count }) ?? "", width: frame.w - 2 * M.margin, cap: 120)
        let cap = Self.capHeight(size)
        let baseline2 = frame.h - frame.bottom - 92
        let baseline1 = baseline2 - 0.92 * size
        let kickerBaseline = baseline1 - cap - 22
        let rows: [(String, Double, Double)] = [(kicker, 22, kickerBaseline), (title[0], size, baseline1), (title.count > 1 ? title[1] : "", size, baseline2)]
        for (i, row) in rows.enumerated() {
            let look = s >= 9 ? Look.enter(s, 9.2 + 0.07 * Double(i), 0.5) : Look.leave(s, 1.55 + 0.05 * Double(i), 0.35)
            Self.text(context, row.0, size: row.1, x: M.margin, baseline: row.2, look: look, opacity: i == 0 ? 0.85 : 1)
        }
    }

    private func drawFirst(_ context: GraphicsContext, _ frame: Frame, s: Double, fit: Bool = false) {
        guard s > 1.8, s < 5.5 else { return }
        for (i, line) in caption.enumerated() {
            let look = fit ? Look() : Look.enter(s, 1.85 + 0.06 * Double(i), 0.5).then(Look.leave(s, 4.95 + 0.05 * Double(i), 0.4))
            Self.text(context, line, size: 30, x: M.margin, baseline: frame.top + 86 + 36 * Double(i), look: look)
        }

        // The number: one slot per digit, as wide as the widest digit, and the group separators.
        let text = number.formatted(.number.grouping(.automatic).locale(Locale(identifier: "en_US")))
        var size = 0.7 * frame.w
        var layout = Self.slots(context, text, size: size)
        if fit {
            size *= min(1, (frame.w - 2 * M.margin) / layout.width)
            layout = Self.slots(context, text, size: size)
        }
        let cap = Self.capHeight(size)
        let baseline = 0.6 * frame.h + cap / 2
        var x0 = M.margin
        if !fit {
            guard s > 2.15, s < 5.2 else { return }
            x0 = M.margin
                + (frame.w - 8) * (1 - easeOutCubic(clamp((s - 2.15) / 0.75)))
                - (layout.width + 0.3 * frame.w + M.margin) * easeInCubic(clamp((s - 2.75) / 2.45))
        }
        let roll = fit ? 1 : easeOutCubic(clamp((s - 2.2) / 1.2))
        let step = cap * 1.25
        var band = context
        band.clip(to: Path(CGRect(x: 0, y: baseline - cap * 1.08, width: frame.w, height: cap * 1.16)))
        var digit = 0
        for slot in layout.slots {
            let x = x0 + slot.x
            if slot.isDigit {
                let target = Double(slot.char.wholeNumberValue ?? 0)
                let position = (target + 10 * Double(digit)) * roll
                let base = floor(position)
                let frac = position - base
                let a = Int(base) % 10, b = (Int(base) + 1) % 10
                // The outgoing digit rises and fades, the next one comes up from below.
                Self.text(band, "\(a)", size: size, x: x + (slot.width - Self.width(band, "\(a)", size: size)) / 2, baseline: baseline - frac * step, look: Look(alpha: 1 - frac))
                if frac > 0.001 {
                    Self.text(band, "\(b)", size: size, x: x + (slot.width - Self.width(band, "\(b)", size: size)) / 2, baseline: baseline + (1 - frac) * step, look: Look(alpha: frac))
                }
                digit += 1
            } else {
                Self.text(band, String(slot.char), size: size, x: x, baseline: baseline)
            }
        }
    }

    private func drawSecond(_ context: GraphicsContext, _ frame: Frame, s: Double, fit: Bool = false) {
        guard fit || (s > 5.3 && s < 9.4) else { return }
        var size = 0.4 * frame.w
        let widest = lines.map { Self.width(context, $0, size: size) }.max() ?? 1
        if fit { size *= min(1, (frame.w - 2 * M.margin) / widest) }
        let pitch = 0.94 * size
        let cap = Self.capHeight(size)
        // The block of lines is centred a little below the middle.
        let firstBaseline = 0.53 * frame.h - pitch * Double(lines.count - 1) / 2 + cap / 2
        for (i, line) in lines.enumerated() {
            let baseline = firstBaseline + pitch * Double(i)
            guard !fit else {
                Self.text(context, line, size: size, x: M.margin, baseline: baseline)
                continue
            }
            let width = Self.width(context, line, size: size)
            let speed = max(0, M.margin + width - 0.62 * frame.w) / 3.5
            let arrive = 5.35 + 0.1 * Double(i)
            let x = M.margin
                + 0.95 * frame.w * (1 - easeOutCubic(clamp((s - arrive) / 0.85)))
                - speed * max(0, s - 5.35)
            let out = clamp((s - 8.8 - 0.05 * Double(i)) / 0.4)
            let look = Look(dx: -0.7 * frame.w * easeInCubic(out), alpha: 1 - smoothstep(0.3, 1, out), blur: M.blur * easeInCubic(out))
            Self.text(context, line, size: size, x: x, baseline: baseline, look: look)
        }
    }

    // MARK: Chrome

    private func drawChrome(_ context: GraphicsContext, _ frame: Frame, s: Double) {
        let w = frame.w
        // Story bars.
        let gap = 4.0, inset = 12.0
        let barWidth = (w - 2 * inset - gap * 2) / 3
        let drain = 1 - easeInOut(clamp((s - M.drain.0) / M.drain.1))
        for (i, bar) in M.bars.enumerated() {
            let rect = CGRect(x: inset + Double(i) * (barWidth + gap), y: frame.top + 8, width: barWidth, height: 3)
            context.fill(capsule(rect), with: .color(.white.opacity(0.32)))
            let fill = clamp((s - bar.0) / (bar.1 - bar.0)) * drain
            if fill > 0 {
                context.fill(capsule(CGRect(x: rect.minX, y: rect.minY, width: max(3, rect.width * fill), height: 3)), with: .color(.white))
            }
        }
        // Header: label and close button.
        let row = frame.top + 32
        Self.text(context, label, size: 15, x: 16, baseline: row + Self.capHeight(15) / 2)
        let close = CGPoint(x: w - 16 - 15, y: row)
        context.fill(Path(ellipseIn: CGRect(x: close.x - 15, y: close.y - 15, width: 30, height: 30)), with: .color(.white.opacity(0.22)))
        var cross = Path()
        cross.move(to: CGPoint(x: close.x - 4.5, y: close.y - 4.5)); cross.addLine(to: CGPoint(x: close.x + 4.5, y: close.y + 4.5))
        cross.move(to: CGPoint(x: close.x + 4.5, y: close.y - 4.5)); cross.addLine(to: CGPoint(x: close.x - 4.5, y: close.y + 4.5))
        context.stroke(cross, with: .color(.white), style: StrokeStyle(lineWidth: 2, lineCap: .round))

        // Bottom corners: pause and share.
        let cy = frame.h - frame.bottom - 12 - 18
        for cx in [16 + 18.0, w - 16 - 18] {
            context.fill(Path(ellipseIn: CGRect(x: cx - 18, y: cy - 18, width: 36, height: 36)), with: .color(.white.opacity(0.22)))
        }
        let left = 16 + 18.0
        for dx in [-3.5, 3.5] {
            context.fill(capsule(CGRect(x: left + dx - 1.5, y: cy - 6.5, width: 3, height: 13)), with: .color(.white))
        }
        let right = w - 16 - 18
        var share = Path()
        share.move(to: CGPoint(x: right, y: cy + 2)); share.addLine(to: CGPoint(x: right, y: cy - 8))
        share.move(to: CGPoint(x: right - 3.5, y: cy - 4.5)); share.addLine(to: CGPoint(x: right, y: cy - 8)); share.addLine(to: CGPoint(x: right + 3.5, y: cy - 4.5))
        share.move(to: CGPoint(x: right - 3, y: cy - 2)); share.addLine(to: CGPoint(x: right - 6, y: cy - 2)); share.addLine(to: CGPoint(x: right - 6, y: cy + 7))
        share.addLine(to: CGPoint(x: right + 6, y: cy + 7)); share.addLine(to: CGPoint(x: right + 6, y: cy - 2)); share.addLine(to: CGPoint(x: right + 3, y: cy - 2))
        context.stroke(share, with: .color(.white), style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
    }

    // MARK: Type

    /// An element's offset, opacity and blur (sigma, pt) during a transition.
    struct Look {
        var dx = 0.0
        var alpha = 1.0
        var blur = 0.0

        /// Comes in from the right, sharpening.
        static func enter(_ s: Double, _ start: Double, _ duration: Double) -> Look {
            let p = clamp((s - start) / duration)
            let e = easeOutCubic(p)
            return Look(dx: M.shift * (1 - e), alpha: e, blur: M.blur * (1 - e))
        }

        /// Leaves to the left, blurring.
        static func leave(_ s: Double, _ start: Double, _ duration: Double) -> Look {
            let q = clamp((s - start) / duration)
            let e = easeInCubic(q)
            return Look(dx: -M.shift * e, alpha: 1 - q, blur: M.blur * e)
        }

        func then(_ other: Look) -> Look {
            Look(dx: dx + other.dx, alpha: alpha * other.alpha, blur: blur + other.blur)
        }
    }

    private static let registered: Void = {
        guard let url = Bundle.main.url(forResource: "KineticStatsStory-InterTight-Bold", withExtension: "ttf") else { return }
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
    }()

    private static func resolve(_ context: GraphicsContext, _ string: String, size: Double, opacity: Double = 1) -> GraphicsContext.ResolvedText {
        context.resolve(Text(string).font(.custom(M.font, fixedSize: size)).foregroundColor(.white.opacity(opacity)))
    }

    static func width(_ context: GraphicsContext, _ string: String, size: Double) -> Double {
        resolve(context, string, size: size).measure(in: CGSize(width: 100_000, height: 100_000)).width
    }

    /// Cap height from the font's own metrics.
    static func capHeight(_ size: Double) -> Double {
        UIFont(name: M.font, size: size).map { Double($0.capHeight) } ?? size * 0.727
    }

    /// The largest size up to `cap` at which `string` fits `width`.
    static func fit(_ context: GraphicsContext, _ string: String, width: Double, cap: Double) -> Double {
        let unit = Self.width(context, string, size: 100)
        return unit > 0 ? min(cap, 100 * width / unit) : cap
    }

    /// Draws `string` with its baseline at `baseline` and its left edge at `x` (plus the look's offset).
    static func text(_ context: GraphicsContext, _ string: String, size: Double, x: Double, baseline: Double, look: Look = Look(), opacity: Double = 1) {
        guard look.alpha > 0.002, !string.isEmpty else { return }
        let resolved = resolve(context, string, size: size, opacity: opacity)
        let measured = resolved.measure(in: CGSize(width: 100_000, height: 100_000))
        var c = context
        c.opacity = look.alpha
        if look.blur > 0.05 { c.addFilter(.blur(radius: look.blur)) }
        c.draw(resolved, at: CGPoint(x: x + look.dx, y: baseline - resolved.firstBaseline(in: measured)), anchor: .topLeading)
    }

    struct Slot {
        let char: Character
        let x: Double
        let width: Double
        var isDigit: Bool { char.isNumber }
    }

    /// Digits get equal slots (the widest digit's advance), so the number does not jitter while it rolls.
    static func slots(_ context: GraphicsContext, _ text: String, size: Double) -> (slots: [Slot], width: Double) {
        let digit = (0...9).map { width(context, "\($0)", size: size) }.max() ?? size * 0.6
        var x = 0.0
        var slots: [Slot] = []
        for char in text {
            let w = char.isNumber ? digit : width(context, String(char), size: size)
            slots.append(Slot(char: char, x: x, width: w))
            x += w
        }
        return (slots, x)
    }
}

/// The mesh behind the story: a base colour and five soft blobs, one palette per card.
enum KineticStatsStoryMesh {
    struct Blob {
        let x, y, ax, ay, fx, fy, px, py, r: Double
    }

    /// Base colour then the five blobs, for the cover, the first card and the second card.
    static let palettes: [[UInt32]] = [
        [0x0d2433, 0x1f8f7d, 0x2c5da8, 0x5fd39a, 0x081520, 0xb9772c],
        [0xff6a00, 0x25d98c, 0xffb300, 0xff4400, 0xff8a00, 0xffd34d],
        [0xff7400, 0xffc02e, 0xff5000, 0x38e0a0, 0xffa000, 0xffe58a],
    ]

    /// Centre (fractions of the screen), swing, frequency (turns per loop), phase and radius
    /// (fraction of the longer side).
    static let blobs: [Blob] = [
        Blob(x: 0.15, y: 0.22, ax: 0.25, ay: 0.08, fx: 1, fy: 2, px: 0.0, py: 1.1, r: 0.42),
        Blob(x: 0.85, y: 0.35, ax: 0.20, ay: 0.12, fx: 1, fy: 1, px: 2.1, py: 0.4, r: 0.48),
        Blob(x: 0.35, y: 0.75, ax: 0.30, ay: 0.10, fx: 2, fy: 1, px: 4.0, py: 2.6, r: 0.5),
        Blob(x: 0.90, y: 0.90, ax: 0.15, ay: 0.08, fx: 1, fy: 2, px: 1.3, py: 3.7, r: 0.4),
        Blob(x: 0.45, y: 0.05, ax: 0.30, ay: 0.06, fx: 1, fy: 1, px: 5.2, py: 0.9, r: 0.35),
    ]
}

private func clamp(_ x: Double) -> Double { min(max(x, 0), 1) }
private func easeOutCubic(_ t: Double) -> Double { 1 - pow(1 - t, 3) }
private func easeInCubic(_ t: Double) -> Double { t * t * t }
private func easeInOut(_ t: Double) -> Double { t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2 }
private func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
    let t = clamp((x - a) / (b - a))
    return t * t * (3 - 2 * t)
}
