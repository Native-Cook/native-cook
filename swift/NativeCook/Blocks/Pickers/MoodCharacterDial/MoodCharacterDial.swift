import SwiftUI
import UIKit

/// The characters the stage can show.
enum MoodCharacterDialFigure {
    case joyful, sleepy, recovery, satisfied, foodie, thoughtful, nostalgic, shopping, scrolling
}

struct MoodCharacterDialMood: Equatable {
    var name: String
    var figure: MoodCharacterDialFigure
    /// The character's colour, as 0xRRGGBB like the three below.
    var body: UInt32
    /// The panel behind the character.
    var stage: UInt32
    /// The two colours of the needle (top to bottom) and of the selected chip (left to right).
    var from: UInt32
    var to: UInt32

    static let samples = [
        MoodCharacterDialMood(name: "Joyful", figure: .joyful, body: 0x57c24a, stage: 0xd3ebb4, from: 0x8fd96b, to: 0xf5d34a),
        MoodCharacterDialMood(name: "Sleepy", figure: .sleepy, body: 0xf5b70f, stage: 0xefe2b4, from: 0xf5c63c, to: 0xf29ac0),
        MoodCharacterDialMood(name: "Recovery Mode", figure: .recovery, body: 0xf26a5e, stage: 0xcfd2c4, from: 0xc9a8f0, to: 0x9ed98a),
        MoodCharacterDialMood(name: "Satisfied", figure: .satisfied, body: 0x3fb5a8, stage: 0xcde6df, from: 0x5fd0c0, to: 0xf5d34a),
        MoodCharacterDialMood(name: "Foodie", figure: .foodie, body: 0xf5980f, stage: 0xf8d9a8, from: 0xf5a53c, to: 0xf26a5e),
        MoodCharacterDialMood(name: "Thoughtful", figure: .thoughtful, body: 0x5b8def, stage: 0xcfddf7, from: 0x7fa8f5, to: 0xb56bf0),
        MoodCharacterDialMood(name: "Nostalgic", figure: .nostalgic, body: 0xb265f0, stage: 0xe4d2f7, from: 0xb56bf0, to: 0xf08a7a),
        MoodCharacterDialMood(name: "Shopping", figure: .shopping, body: 0xff8fe0, stage: 0xf9d5fb, from: 0xff8fe0, to: 0xb56bf0),
        MoodCharacterDialMood(name: "Scroll Mode", figure: .scrolling, body: 0xf0434a, stage: 0xf8c3b0, from: 0xf0434a, to: 0xf5980f),
    ]
}

/// A mood picker in two halves. On top, a pastel panel holds one large flat character per mood, laid
/// out as a carousel. Below, on a near-black panel, a title, a row of thin bars under a bell-shaped
/// envelope with a gradient needle in the middle, and a row of outlined chips. Dragging anywhere moves
/// the chips with the finger, the bars under the fixed envelope and the characters one panel per mood,
/// while the panel's colour and the needle's gradient cross-fade. On release the row snaps a chip
/// under the needle; it fills with the mood's gradient and the character lands with a small squash.
struct MoodCharacterDial: View {
    var title = "Select your\nweekend mood"
    var moods = MoodCharacterDialMood.samples
    /// Index of the mood selected at first, and the one the self-playing demo rests on.
    var initial = 1
    /// Steps and flicks through the moods by itself, in a loop.
    var autoPlay = false
    /// Called with the index of the mood the dial is sent to.
    var onSelect: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = Motion()
    /// Counts touches: each one stops the loop and restarts the wait before it resumes.
    @State private var touches = 0

    /// What moves, sampled against the `TimelineView` clock.
    private final class Motion {
        /// The dial's position as a mood index; whole numbers are the snap points.
        var pos = Track(0)
        /// The selected chip's gradient: 0 an outline … 1 filled.
        var fill = Track(1)
        /// The mood the dial is at, or heading for.
        var target = 0
        /// The chip that carries the gradient: the one the dial last landed on.
        var filled = 0
        /// A move is under way; it ends when the dial first reaches its target.
        var moving = false
        var dragging = false
        /// The finger has moved far enough to be a drag and not a tap.
        var dragged = false
        /// The chip row's offset when the finger went down.
        var dragFrom = 0.0
        /// When the dial last reached a target: the character there squashes.
        var landed = Date.distantPast
        /// The mood under the needle at the last frame, for the haptic ticks.
        var ticked = 0
        /// The moves come from a finger, so they tick; the script's do not.
        var felt = false
        var began = Date.now
        /// Each chip's width and its centre along the row.
        var widths: [Double] = []
        var centres: [Double] = []
        /// The component's width, for finding the chip under a tap.
        var width = Metrics.design
        let haptic = UISelectionFeedbackGenerator()

        func measure(_ names: [String]) {
            let font = UIFont.systemFont(ofSize: Metrics.chipFont, weight: .light)
            widths = names.map { ceil(($0 as NSString).size(withAttributes: [.font: font]).width) + Metrics.chipPad * 2 }
            var x = 0.0
            centres = widths.map { width in
                defer { x += width + Metrics.chipGap }
                return x + width / 2
            }
        }

        /// The row's offset when the dial is at `p`: chip centres, joined by straight lines.
        func offset(at p: Double) -> Double {
            guard centres.count > 1 else { return centres.first ?? 0 }
            let i = min(max(Int(p.rounded(.down)), 0), centres.count - 2)
            return centres[i] + (centres[i + 1] - centres[i]) * (p - Double(i))
        }

        /// The inverse of `offset(at:)`.
        func index(at x: Double) -> Double {
            guard centres.count > 1 else { return 0 }
            var i = 0
            while i < centres.count - 2, x > centres[i + 1] { i += 1 }
            return Double(i) + (x - centres[i]) / (centres[i + 1] - centres[i])
        }

        /// Runs once a frame: ticks as moods pass the needle, and notices the landing.
        func step(_ now: Date) {
            let p = pos.value(at: now)
            let tick = min(max(Int(p.rounded()), 0), max(0, centres.count - 1))
            if tick != ticked {
                ticked = tick
                if felt { haptic.selectionChanged() }
            }
            if moving, !dragging, abs(p - Double(target)) < Metrics.landWithin {
                moving = false
                landed = now
                filled = target
                fill.retarget(1, .tween(Metrics.fillIn, .linear))
            }
        }
    }

    private var auto: Bool { autoPlay && !reduceMotion && moods.count > 1 }
    private var home: Int { min(max(initial, 0), moods.count - 1) }

    var body: some View {
        GeometryReader { proxy in
            let width = min(proxy.size.width, Metrics.maxWidth)
            TimelineView(.animation) { timeline in
                let now = timeline.date
                let _ = motion.width = width
                let _ = motion.step(now)
                let p = motion.pos.value(at: now)
                let fill = motion.fill.value(at: now)
                let seconds = reduceMotion ? 0 : now.timeIntervalSince(motion.began)
                let squash = reduceMotion ? 0 : squashAt(now.timeIntervalSince(motion.landed))
                let row = motion.offset(at: p)

                ZStack {
                    if motion.widths.count == moods.count {
                        Canvas { context, size in
                            drawStage(context, size: size, p: p, seconds: seconds, squash: squash)
                        }
                        .frame(width: width, height: Metrics.cardH)
                        .position(x: width / 2, y: Metrics.cardH / 2)

                        Text(title)
                            .font(.system(size: 30, weight: .bold, design: .serif))
                            .foregroundStyle(.white)
                            .multilineTextAlignment(.center)
                            .fixedSize()
                            .position(x: width / 2, y: Metrics.titleY)

                        Canvas { context, size in
                            drawBars(context, size: size, p: p)
                        }
                        .frame(width: width, height: Metrics.barsH)
                        .position(x: width / 2, y: Metrics.barsY)

                        ForEach(moods.indices, id: \.self) { i in
                            chip(i, fill: i == motion.filled ? fill : 0)
                                .position(x: width / 2 + motion.centres[i] - row, y: Metrics.chipsY)
                        }
                    }
                }
                .frame(width: width, height: Metrics.height)
                .clipped()
                .contentShape(Rectangle())
                .gesture(drag)
            }
            .frame(width: width, height: Metrics.height)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .frame(height: Metrics.height)
        .onAppear { reset() }
        .onChange(of: moods) { reset() }
        .task(id: LoopKey(auto: auto, touches: touches)) { await runLoop() }
    }

    private struct LoopKey: Equatable {
        let auto: Bool
        let touches: Int
    }

    // MARK: Parts

    private func chip(_ i: Int, fill: Double) -> some View {
        let mood = moods[i]
        return Text(mood.name)
            .font(.system(size: Metrics.chipFont, weight: .light))
            .foregroundStyle(blend(0xffffff, Metrics.panel, fill))
            .lineLimit(1)
            .frame(width: motion.widths[i], height: Metrics.chipH)
            .background(
                Capsule()
                    .fill(LinearGradient(colors: [Color(hex: mood.from), Color(hex: mood.to)], startPoint: .leading, endPoint: .trailing))
                    .opacity(fill)
            )
            .overlay(Capsule().strokeBorder(Color(hex: 0x5a6264).opacity(1 - fill), lineWidth: 1))
            .accessibilityAddTraits(i == motion.target ? [.isButton, .isSelected] : .isButton)
    }

    /// The pastel panel and the characters on it, clipped to the panel.
    private func drawStage(_ context: GraphicsContext, size: CGSize, p: Double, seconds: Double, squash: Double) {
        var context = context
        let card = CGRect(x: Metrics.cardInset, y: 0, width: size.width - Metrics.cardInset * 2, height: size.height)
        context.clip(to: Path(roundedRect: card, cornerRadius: Metrics.cardRadius, style: .continuous))
        context.fill(Path(card), with: .color(tone(\.stage, p)))

        let scale = size.width / Metrics.design
        let breath = 0.012 * sin(tau * seconds / Metrics.breathTime)
        for i in moods.indices where abs(Double(i) - p) < 1.7 {
            // Squash and breathe about the point where the character meets the panel's bottom edge.
            let press = (i == motion.target ? squash : 0) - breath
            var figure = context
            figure.translateBy(x: size.width / 2 + (Double(i) - p) * Metrics.figureStep * scale, y: size.height)
            figure.scaleBy(x: scale * (1 + press), y: scale * (1 - press))
            figure.translateBy(x: 0, y: -Metrics.figureRise)
            moodCharacterDialDraw(
                figure, figure: moods[i].figure, body: Color(hex: moods[i].body),
                seconds: seconds, near: clamp01(1 - abs(Double(i) - p) * 1.5)
            )
        }
    }

    /// The bars slide under an envelope that stays put, so each grows towards the middle; the needle is fixed.
    private func drawBars(_ context: GraphicsContext, size: CGSize, p: Double) {
        let middle = size.width / 2
        let base = size.height - Metrics.needleDrop
        let shift = p * Metrics.barsPerMood
        let part = shift - shift.rounded(.down)
        for k in -Metrics.barsAside - 1...Metrics.barsAside + 1 {
            let x = (Double(k) - part) * Metrics.barGap
            guard abs(x) <= Double(Metrics.barsAside) * Metrics.barGap + 0.5 else { continue }
            let bell = exp(-pow(x / Metrics.bellWidth, 2))
            let height = mix(Metrics.barLow, Metrics.barHigh, bell)
            let bar = CGRect(x: middle + x - 1, y: base - height, width: 2, height: height)
            context.fill(Path(roundedRect: bar, cornerRadius: 1), with: .color(.white.opacity(mix(0.05, 0.45, bell))))
        }
        let needle = CGRect(x: middle - 1.5, y: 0, width: 3, height: size.height)
        context.fill(
            Path(roundedRect: needle, cornerRadius: 1.5),
            with: .linearGradient(Gradient(colors: [tone(\.from, p), tone(\.to, p)]), startPoint: CGPoint(x: middle, y: 0), endPoint: CGPoint(x: middle, y: size.height))
        )
    }

    /// A colour of the mood at `p`, cross-faded with its neighbour's in between.
    private func tone(_ key: KeyPath<MoodCharacterDialMood, UInt32>, _ p: Double) -> Color {
        guard moods.count > 1 else { return Color(hex: moods.first?[keyPath: key] ?? 0) }
        let i = min(max(Int(p.rounded(.down)), 0), moods.count - 2)
        return blend(moods[i][keyPath: key], moods[i + 1][keyPath: key], clamp01(p - Double(i)))
    }

    // MARK: The dial's moves

    private var drag: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                if !motion.dragging {
                    // The finger catches the dial wherever it is.
                    touches += 1
                    motion.dragging = true
                    motion.dragged = false
                    motion.felt = true
                    motion.pos.set(motion.pos.value(at: .now))
                    motion.dragFrom = motion.offset(at: motion.pos.value(at: .now))
                }
                guard motion.dragged || abs(value.translation.width) > Metrics.tapSlop else { return }
                if !motion.dragged {
                    motion.dragged = true
                    motion.moving = true
                    motion.fill.retarget(0, .tween(Metrics.fillOut, .linear))
                }
                motion.pos.set(banded(motion.index(at: motion.dragFrom - value.translation.width)))
            }
            .onEnded { value in
                touches += 1
                motion.dragging = false
                let p = motion.pos.value(at: .now)
                if motion.dragged {
                    // The row keeps the finger's speed and settles on the chip it would coast to.
                    let spacing = max(1, motion.offset(at: p + 0.5) - motion.offset(at: p - 0.5))
                    let speed = -value.velocity.width / spacing
                    select(Int((p + speed * Metrics.coast).rounded()), velocity: speed)
                } else if let tapped = chip(at: value.location) {
                    select(tapped)
                } else {
                    select(Int(p.rounded()))
                }
            }
    }

    /// The chip under a point of the component, if any.
    private func chip(at point: CGPoint) -> Int? {
        guard abs(point.y - Metrics.chipsY) <= Metrics.chipH / 2 + 8 else { return nil }
        let x = point.x - motion.width / 2 + motion.offset(at: motion.pos.value(at: .now))
        return moods.indices.first { abs(x - motion.centres[$0]) <= (motion.widths[$0] + Metrics.chipGap) / 2 }
    }

    /// Past either end the row follows the finger at a third of its speed.
    private func banded(_ p: Double) -> Double {
        let last = Double(moods.count - 1)
        if p < 0 { return p * Metrics.band }
        if p > last { return last + (p - last) * Metrics.band }
        return p
    }

    private func reset() {
        motion.measure(moods.map(\.name))
        motion.target = home
        motion.filled = home
        motion.ticked = home
        motion.pos = Track(Double(home))
        motion.fill = Track(1)
        motion.moving = false
        motion.began = .now
    }

    /// Sends the dial to mood `index`: on a spring that is softer the further it has to go, or, for
    /// the script's long way home, on an ease-in-out glide.
    private func select(_ index: Int, velocity: Double = 0, glide: Bool = false) {
        guard !moods.isEmpty else { return }
        let index = min(max(index, 0), moods.count - 1)
        let p = motion.pos.value(at: .now)
        let far = abs(Double(index) - p)
        if far > Metrics.landWithin { motion.fill.retarget(0, .tween(Metrics.fillOut, .linear)) }
        let changed = index != motion.target
        motion.target = index
        motion.moving = true
        if reduceMotion {
            motion.pos.set(Double(index))
        } else if glide {
            motion.pos.retarget(Double(index), .tween(Metrics.glideTime, easeInOut))
        } else {
            let stiffness = Metrics.stiffness / pow(max(1, far), 0.75)
            motion.pos.retarget(Double(index), .spring(Spring(mass: 1, stiffness: stiffness, damping: Metrics.dampingRatio * 2 * stiffness.squareRoot())), velocity: velocity)
        }
        if changed { onSelect?(index) }
    }

    /// The script a finger would follow: a step, a flick, a step, and a long glide home.
    private func runLoop() async {
        guard auto else { return }
        do {
            if touches > 0 {
                // A touch took over: wait, go back to the resting mood, and start over from there.
                try await Task.sleep(for: .seconds(Metrics.resumeAfter))
                guard !motion.dragging else { return }
                motion.felt = false
                select(home)
                try await Task.sleep(for: .seconds(Metrics.homeTime))
            }
            let clock = ContinuousClock()
            var start = clock.now
            while true {
                for step in Metrics.script {
                    try await clock.sleep(until: start + .seconds(step.at))
                    select(home + step.by, glide: step.glide)
                }
                start += .seconds(Metrics.loop)
            }
        } catch {}
    }
}

private enum Metrics {
    /// The width the sizes below were drawn for; the characters scale with a narrower screen.
    static let design = 402.0
    static let maxWidth = 430.0
    static let height = 630.0
    static let panel: UInt32 = 0x141b1c

    // The pastel panel and its characters.
    static let cardH = 340.0
    static let cardInset = 8.0
    static let cardRadius = 32.0
    /// Distance between two characters, and how far a character's centre sits above the panel's bottom edge.
    static let figureStep = 340.0
    static let figureRise = 125.0
    static let breathTime = 4.0

    static let titleY = 392.0

    // The bars: 31 across, sliding five places per mood.
    static let barsY = 494.0
    static let barsH = 96.0
    static let needleDrop = 6.0
    static let barGap = 12.0
    static let barsAside = 15
    static let barsPerMood = 5.0
    static let barLow = 10.0
    static let barHigh = 84.0
    static let bellWidth = 95.0

    // The chips.
    static let chipsY = 598.0
    static let chipH = 48.0
    static let chipPad = 22.0
    static let chipGap = 12.0
    static let chipFont = 15.0

    // Snapping.
    static let stiffness = 220.0
    static let dampingRatio = 0.74
    static let glideTime = 1.1
    /// Seconds of the finger's speed the row coasts for.
    static let coast = 0.2
    static let band = 0.35
    static let tapSlop = 6.0
    /// The dial has landed once it is this close (in moods) to its target.
    static let landWithin = 0.03
    static let fillIn = 0.2
    static let fillOut = 0.12

    // autoPlay (seconds): when each move starts, and how many moods from the resting one it goes to.
    static let loop = 8.0
    static let script: [(at: Double, by: Int, glide: Bool)] = [
        (1.0, 1, false),
        (2.4, 4, false),
        (4.1, 5, false),
        (5.4, 0, true),
    ]
    /// After a touch the loop waits this long, then takes `homeTime` to return to the resting mood.
    static let resumeAfter = 2.0
    static let homeTime = 1.2
}

/// A number that jumps, eases or springs towards a target, sampled against the `TimelineView` clock.
private struct Track {
    enum Kind {
        case jump
        case tween(Double, UnitCurve)
        case spring(Spring)
    }

    private var from: Double
    private var to: Double
    private var speed = 0.0
    private var start = Date.distantPast
    private var kind = Kind.jump

    init(_ value: Double) {
        from = value
        to = value
    }

    func value(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        guard t > 0 else { return from }
        switch kind {
        case .jump: return to
        case .tween(let duration, let curve): return t >= duration ? to : from + (to - from) * curve.value(at: t / duration)
        case .spring(let spring): return from + spring.value(target: to - from, initialVelocity: speed, time: t)
        }
    }

    private func velocity(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        guard t > 0, case .spring(let spring) = kind else { return 0 }
        return spring.velocity(target: to - from, initialVelocity: speed, time: t)
    }

    mutating func set(_ value: Double) {
        from = value
        to = value
        kind = .jump
    }

    /// Heads for `target` from where it is now, keeping its own speed unless `velocity` gives another.
    mutating func retarget(_ target: Double, _ kind: Kind, velocity: Double? = nil) {
        let now = Date.now
        let value = value(at: now)
        speed = velocity ?? self.velocity(at: now)
        from = value
        to = target
        start = now
        self.kind = kind
    }
}

private let easeInOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.45, y: 0), endControlPoint: UnitPoint(x: 0.55, y: 1))

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

private func blend(_ a: UInt32, _ b: UInt32, _ t: Double) -> Color {
    func channel(_ shift: UInt32) -> Double {
        mix(Double((a >> shift) & 0xff), Double((b >> shift) & 0xff), t) / 255
    }
    return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0), opacity: 1)
}

/// The landing: the character dips 4% within 80 ms of arriving and rings out in about half a second.
private func squashAt(_ t: Double) -> Double {
    guard t > 0, t < 0.6 else { return 0 }
    return 0.04 / 0.524 * exp(-7 * t) * sin(14.5 * t)
}

// MARK: The characters

private let ink = Color(hex: 0x2b1b17)
private let blush = Color(hex: 0xff8fa0, opacity: 0.6)
private let rose = Color(hex: 0xe8384f)

/// Draws one character around the origin, which is the centre of its 330 pt body. `near` is 1 when
/// the character is the one in the middle of the panel, and fades what floats around it.
private func moodCharacterDialDraw(_ context: GraphicsContext, figure: MoodCharacterDialFigure, body: Color, seconds: Double, near: Double) {
    let fill = GraphicsContext.Shading.color(body)
    let line = StrokeStyle(lineWidth: 5.5, lineCap: .round, lineJoin: .round)

    switch figure {
    case .joyful:
        context.fill(lumps(6, 0.045, 0), with: fill)
        cheeks(context)
        eyes(context, .smiling, line)
        mouth(context, .grin, line)
    case .sleepy:
        context.fill(lumps(1, 0, 0), with: fill)
        eyes(context, .asleep, line)
        mouth(context, .round, line)
        // Three letters rise one after the other, growing and fading as they go.
        for letter in 0..<3 {
            let u = (seconds / 2 + Double(letter) / 3).truncatingRemainder(dividingBy: 1)
            let size = 5 + 7 * u
            let x = 84 + 40 * u
            let y = -128 - 70 * u
            var z = Path()
            z.move(to: CGPoint(x: x - size, y: y - size))
            z.addLine(to: CGPoint(x: x + size, y: y - size))
            z.addLine(to: CGPoint(x: x - size, y: y + size))
            z.addLine(to: CGPoint(x: x + size, y: y + size))
            context.stroke(z, with: .color(Color(hex: 0x2b3fd6, opacity: sin(.pi * u) * near)), style: StrokeStyle(lineWidth: size * 0.5, lineCap: .round, lineJoin: .round))
        }
    case .recovery:
        context.fill(lumps(4, 0.06, 0.6), with: fill)
        context.fill(Path(roundedRect: CGRect(x: -4.5, y: -143, width: 9, height: 30), cornerRadius: 3), with: .color(.white))
        context.fill(Path(roundedRect: CGRect(x: -15, y: -132.5, width: 30, height: 9), cornerRadius: 3), with: .color(.white))
        eyes(context, .asleep, line)
        mouth(context, .smile, line)
    case .satisfied:
        context.fill(block(300, 360, 110), with: fill)
        cheeks(context)
        eyes(context, .smiling, line)
        mouth(context, .smile, line)
    case .foodie:
        context.fill(lumps(3, 0.07, 0), with: fill)
        cheeks(context)
        eyes(context, .open(0, 0.5), line)
        mouth(context, .lick, line)
    case .thoughtful:
        context.fill(block(290, 360, 145), with: fill)
        for (x, y, r) in [(92.0, -108.0, 7.0), (116.0, -138.0, 10.0), (146.0, -172.0, 14.0)] {
            context.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)), with: .color(.white))
        }
        eyes(context, .open(0.6, -0.6), line)
        mouth(context, .flat, line)
    case .nostalgic:
        context.fill(lumps(5, 0.05, 0.3), with: fill)
        context.fill(sparkle(92, -118, 15), with: .color(.white))
        context.fill(sparkle(116, -88, 8), with: .color(.white))
        eyes(context, .open(-0.5, -0.6), line)
        mouth(context, .smirk, line)
    case .shopping:
        // A bag: two handles behind a rounded square, and a heart under the face.
        for x in [-32.0, 32.0] {
            var handle = Path()
            handle.addArc(center: CGPoint(x: x, y: -163), radius: 26, startAngle: .degrees(180), endAngle: .degrees(360), clockwise: false)
            context.stroke(handle, with: .color(rose), style: StrokeStyle(lineWidth: 8, lineCap: .round))
        }
        context.fill(block(280, 360, 44), with: fill)
        eyes(context, .smiling, line)
        mouth(context, .smile, line)
        context.fill(heart(0, 30, 26), with: .color(rose))
    case .scrolling:
        context.fill(lumps(8, 0.04, 0), with: fill)
        eyes(context, .open(0.8, 0.2), line)
        mouth(context, .flat, line)
        // A phone held up at the side, seen from the back.
        var phone = context
        phone.translateBy(x: 100, y: -24)
        phone.rotate(by: .degrees(-10))
        phone.fill(Path(roundedRect: CGRect(x: -23, y: -39, width: 46, height: 78), cornerRadius: 10), with: .color(Color(hex: 0x1e1e24)))
        phone.fill(Path(ellipseIn: CGRect(x: -15, y: -31, width: 13, height: 13)), with: .color(Color(hex: 0x3a3a44)))
    }
}

private enum Eyes {
    /// Closed, curving down.
    case asleep
    /// Closed, curving up.
    case smiling
    /// Open, looking towards `x`, `y` (each −1…1).
    case open(Double, Double)
}

private enum Mouth {
    case round, smile, grin, flat, smirk, lick
}

private func eyes(_ context: GraphicsContext, _ kind: Eyes, _ line: StrokeStyle) {
    for x in [-46.0, 46.0] {
        switch kind {
        case .asleep:
            var lid = Path()
            lid.move(to: CGPoint(x: x - 19, y: -80))
            lid.addQuadCurve(to: CGPoint(x: x + 19, y: -80), control: CGPoint(x: x, y: -60))
            context.stroke(lid, with: .color(ink), style: line)
        case .smiling:
            var lid = Path()
            lid.move(to: CGPoint(x: x - 19, y: -70))
            lid.addQuadCurve(to: CGPoint(x: x + 19, y: -70), control: CGPoint(x: x, y: -98))
            context.stroke(lid, with: .color(ink), style: line)
        case .open(let dx, let dy):
            let pupil = CGPoint(x: x + dx * 7, y: -78 + dy * 7)
            context.fill(Path(ellipseIn: CGRect(x: x - 18, y: -96, width: 36, height: 36)), with: .color(.white))
            context.fill(Path(ellipseIn: CGRect(x: pupil.x - 9, y: pupil.y - 9, width: 18, height: 18)), with: .color(ink))
            context.fill(Path(ellipseIn: CGRect(x: pupil.x - 6, y: pupil.y - 6, width: 6, height: 6)), with: .color(.white))
        }
    }
}

private func mouth(_ context: GraphicsContext, _ kind: Mouth, _ line: StrokeStyle) {
    var path = Path()
    switch kind {
    case .round:
        context.fill(Path(ellipseIn: CGRect(x: -14, y: -41, width: 28, height: 34)), with: .color(ink))
        context.fill(Path(ellipseIn: CGRect(x: -9, y: -21, width: 18, height: 12)), with: .color(Color(hex: 0xf26a5e)))
    case .smile, .lick:
        path.move(to: CGPoint(x: -22, y: -32))
        path.addQuadCurve(to: CGPoint(x: 22, y: -32), control: CGPoint(x: 0, y: -8))
        if case .lick = kind {
            context.fill(Path(ellipseIn: CGRect(x: 2, y: -25, width: 20, height: 18)), with: .color(rose))
        }
        context.stroke(path, with: .color(ink), style: line)
    case .grin:
        path.move(to: CGPoint(x: -28, y: -36))
        path.addLine(to: CGPoint(x: 28, y: -36))
        path.addQuadCurve(to: CGPoint(x: -28, y: -36), control: CGPoint(x: 0, y: 14))
        path.closeSubpath()
        context.fill(path, with: .color(ink))
        context.stroke(path, with: .color(ink), style: StrokeStyle(lineWidth: 4, lineJoin: .round))
        context.fill(Path(ellipseIn: CGRect(x: -11, y: -24, width: 22, height: 11)), with: .color(Color(hex: 0xf26a5e)))
    case .flat:
        path.move(to: CGPoint(x: -13, y: -26))
        path.addLine(to: CGPoint(x: 13, y: -26))
        context.stroke(path, with: .color(ink), style: line)
    case .smirk:
        path.move(to: CGPoint(x: -14, y: -30))
        path.addQuadCurve(to: CGPoint(x: 18, y: -34), control: CGPoint(x: 6, y: -14))
        context.stroke(path, with: .color(ink), style: line)
    }
}

private func cheeks(_ context: GraphicsContext) {
    for x in [-84.0, 84.0] {
        context.fill(Path(ellipseIn: CGRect(x: x - 14, y: -58, width: 28, height: 28)), with: .color(blush))
    }
}

/// A circle of radius 165 whose edge swells in and out `count` times by the share `depth`.
private func lumps(_ count: Double, _ depth: Double, _ turn: Double) -> Path {
    var path = Path()
    for step in 0..<96 {
        let angle = Double(step) / 96 * tau
        let radius = 165 * (1 + depth * sin(count * angle + turn))
        let point = CGPoint(x: cos(angle) * radius, y: sin(angle) * radius)
        if step == 0 { path.move(to: point) } else { path.addLine(to: point) }
    }
    path.closeSubpath()
    return path
}

/// A rounded rectangle whose top edge is where the circle's would be.
private func block(_ width: Double, _ height: Double, _ radius: Double) -> Path {
    Path(roundedRect: CGRect(x: -width / 2, y: -165, width: width, height: height), cornerRadius: radius, style: .circular)
}

private func sparkle(_ x: Double, _ y: Double, _ size: Double) -> Path {
    let centre = CGPoint(x: x, y: y)
    var path = Path()
    path.move(to: CGPoint(x: x, y: y - size))
    path.addQuadCurve(to: CGPoint(x: x + size, y: y), control: centre)
    path.addQuadCurve(to: CGPoint(x: x, y: y + size), control: centre)
    path.addQuadCurve(to: CGPoint(x: x - size, y: y), control: centre)
    path.addQuadCurve(to: CGPoint(x: x, y: y - size), control: centre)
    return path
}

private func heart(_ x: Double, _ y: Double, _ s: Double) -> Path {
    var path = Path()
    path.move(to: CGPoint(x: x, y: y + s * 0.9))
    path.addCurve(to: CGPoint(x: x - s, y: y - s * 0.2), control1: CGPoint(x: x - s * 0.5, y: y + s * 0.55), control2: CGPoint(x: x - s, y: y + s * 0.15))
    path.addCurve(to: CGPoint(x: x, y: y - s * 0.45), control1: CGPoint(x: x - s, y: y - s * 0.75), control2: CGPoint(x: x - s * 0.3, y: y - s * 0.9))
    path.addCurve(to: CGPoint(x: x + s, y: y - s * 0.2), control1: CGPoint(x: x + s * 0.3, y: y - s * 0.9), control2: CGPoint(x: x + s, y: y - s * 0.75))
    path.addCurve(to: CGPoint(x: x, y: y + s * 0.9), control1: CGPoint(x: x + s, y: y + s * 0.15), control2: CGPoint(x: x + s * 0.5, y: y + s * 0.55))
    return path
}

#Preview {
    ZStack {
        Color(hex: 0x141b1c).ignoresSafeArea()
        MoodCharacterDial(autoPlay: true)
    }
}
