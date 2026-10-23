import SwiftUI
import UIKit

/// The landscape drawn on a card. Colours are 0xRRGGBB.
struct CylinderMonthCarouselScene: Equatable {
    /// The sky, from the top of the card down to the horizon.
    var sky: UInt32
    var haze: UInt32
    var sun: UInt32
    /// Three ranges of hills, from the furthest to the ground in front.
    var far: UInt32
    var mid: UInt32
    var near: UInt32
    /// The round trees and the pines that stand on the middle range.
    var tree: UInt32
    var pine: UInt32
    /// Where the sun stands across the card, 0…1.
    var sunX: Double
    /// Any number: it shapes the hills and places the trees.
    var seed: Double
}

struct CylinderMonthCarouselCard: Equatable {
    var title: String
    var artist: String
    /// A lock in the card's corner instead of a heart.
    var locked: Bool
    /// The colour the card casts under the carousel, as 0xRRGGBB.
    var glow: UInt32
    var scene: CylinderMonthCarouselScene

    static let samples = [
        CylinderMonthCarouselCard(
            title: "Autumn Aura", artist: "Mara Lindqvist", locked: true, glow: 0xb3261a,
            scene: CylinderMonthCarouselScene(sky: 0x3a3d42, haze: 0xb9a58c, sun: 0xf3d9a4, far: 0x5a4a44, mid: 0x8f2f1c, near: 0xc2691f, tree: 0xc8331b, pine: 0x1f2326, sunX: 0.7, seed: 0.4)
        ),
        CylinderMonthCarouselCard(
            title: "Rust Valley", artist: "Mara Lindqvist", locked: true, glow: 0xc2541d,
            scene: CylinderMonthCarouselScene(sky: 0x2b2f3a, haze: 0xe09a5a, sun: 0xffd9a0, far: 0x6b3a2e, mid: 0xa8431f, near: 0x3b2a25, tree: 0xe2582a, pine: 0x2a1d1a, sunX: 0.3, seed: 1.7)
        ),
        CylinderMonthCarouselCard(
            title: "First Frost", artist: "Mara Lindqvist", locked: false, glow: 0x6f8fa8,
            scene: CylinderMonthCarouselScene(sky: 0x5b6b7a, haze: 0xdfe6ea, sun: 0xffffff, far: 0x8394a3, mid: 0x51616f, near: 0xc9d3d9, tree: 0xa23a26, pine: 0x2b3a45, sunX: 0.55, seed: 2.9)
        ),
        CylinderMonthCarouselCard(
            title: "Ember Lake", artist: "Mara Lindqvist", locked: true, glow: 0xd2441c,
            scene: CylinderMonthCarouselScene(sky: 0x1c1e2a, haze: 0xe2552b, sun: 0xffb36b, far: 0x40222b, mid: 0x7a1f1f, near: 0x1a1416, tree: 0xf07a2a, pine: 0x120e10, sunX: 0.42, seed: 4.2)
        ),
        CylinderMonthCarouselCard(
            title: "Cinder Ridge", artist: "Mara Lindqvist", locked: false, glow: 0x8a3a22,
            scene: CylinderMonthCarouselScene(sky: 0x23252b, haze: 0x7a7f88, sun: 0xd8d2c4, far: 0x4a4d55, mid: 0x2e3036, near: 0x8a3a22, tree: 0xb8421f, pine: 0x16181c, sunX: 0.78, seed: 5.6)
        ),
        CylinderMonthCarouselCard(
            title: "Maple Hollow", artist: "Mara Lindqvist", locked: true, glow: 0xe07a1f,
            scene: CylinderMonthCarouselScene(sky: 0x40342f, haze: 0xf0c27a, sun: 0xfff0c0, far: 0xa05a2c, mid: 0xd1452a, near: 0x5a2a1c, tree: 0xf2a23a, pine: 0x3a1c14, sunX: 0.25, seed: 6.8)
        ),
        CylinderMonthCarouselCard(
            title: "Amber Dusk", artist: "Mara Lindqvist", locked: false, glow: 0xc8641f,
            scene: CylinderMonthCarouselScene(sky: 0x2a2440, haze: 0xf2a65a, sun: 0xffe1a8, far: 0x5a3a5a, mid: 0x8a3a3a, near: 0x2a1c26, tree: 0xf26a3a, pine: 0x1c1422, sunX: 0.62, seed: 8.1)
        ),
        CylinderMonthCarouselCard(
            title: "Charcoal Pines", artist: "Mara Lindqvist", locked: true, glow: 0x56606a,
            scene: CylinderMonthCarouselScene(sky: 0x15181c, haze: 0x56606a, sun: 0xc8d0d6, far: 0x2e353c, mid: 0x1d2227, near: 0x3a4148, tree: 0x7a2a1c, pine: 0x0e1114, sunX: 0.35, seed: 9.3)
        ),
    ]
}

/// A month of tall picture cards standing on a drum seen from outside. The card in front faces the
/// viewer; its neighbours are turned away around the vertical axis, pushed back and darkened. A
/// horizontal drag turns the drum, which snaps to the nearest card with a small overshoot. Under it a
/// strip of ticks, one per card and bunched towards its far end, slides to keep the front card's tick
/// in the middle, above the month's name and a glow in the front card's colour.
struct CylinderMonthCarousel: View {
    var month = "Sep 2024"
    var cards = CylinderMonthCarouselCard.samples
    /// Index of the card in front at first, and the one the self-playing demo rests on.
    var initial = 0
    /// Turns and flings the drum by itself, in a loop.
    var autoPlay = false
    /// Called with the index of the card the drum is sent to.
    var onSelect: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = Motion()
    /// Counts touches: each one stops the loop and restarts the wait before it resumes.
    @State private var touches = 0

    /// What moves, sampled against the `TimelineView` clock.
    private final class Motion {
        /// The drum's position as a card index; whole numbers are the snap points.
        var pos = Track(0)
        /// The front card's title and artist: 0 hidden … 1 shown.
        var caption = Track(1)
        /// The card the drum is at, or heading for.
        var target = 0
        /// The card that carries the captions: the one the drum last landed on.
        var captioned = 0
        /// A turn is under way; it ends when the drum first reaches its target.
        var moving = false
        var dragging = false
        /// The finger has moved far enough to be a drag and not a tap.
        var dragged = false
        /// The drum's position when the finger went down.
        var dragFrom = 0.0
        /// The card in front at the last frame, for the haptic ticks.
        var ticked = 0
        /// The turns come from a finger, so they tick; the script's do not.
        var felt = false
        var count = 0
        let haptic = UISelectionFeedbackGenerator()

        /// Runs once a frame: ticks as cards pass the front, and notices the landing.
        func step(_ now: Date) {
            let p = pos.value(at: now)
            let tick = min(max(Int(p.rounded()), 0), max(0, count - 1))
            if tick != ticked {
                ticked = tick
                if felt { haptic.selectionChanged() }
            }
            if moving, !dragging, abs(p - Double(target)) < Metrics.landWithin {
                moving = false
                captioned = target
                caption.retarget(1, .tween(Metrics.captionIn, Ease.out))
            }
        }
    }

    private var auto: Bool { autoPlay && !reduceMotion && cards.count > 1 }
    private var home: Int { min(max(initial, 0), cards.count - 1) }

    var body: some View {
        GeometryReader { proxy in
            let width = min(proxy.size.width, Metrics.maxWidth)
            TimelineView(.animation) { timeline in
                let now = timeline.date
                let _ = motion.step(now)
                let p = motion.pos.value(at: now)
                let caption = motion.caption.value(at: now)

                ZStack {
                    Canvas { context, size in
                        drawFloor(context, size: size, p: p)
                    }
                    .frame(width: width, height: Metrics.height)

                    ForEach(cards.indices, id: \.self) { i in
                        let d = Double(i) - p
                        if abs(d) * Metrics.turn < 89 {
                            let place = cylinderMonthCarouselPlace(d)
                            card(i, away: abs(d), caption: i == motion.captioned ? caption : 0)
                                .rotation3DEffect(.degrees(d * Metrics.turn), axis: (x: 0, y: 1, z: 0), perspective: Metrics.cardH / Metrics.eye)
                                .scaleEffect(place.scale)
                                .position(x: width / 2 + place.x, y: Metrics.cardH / 2)
                                .zIndex(-abs(d))
                        }
                    }

                    Text(month)
                        .font(.system(size: 21, weight: .semibold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .fixedSize()
                        .position(x: width / 2, y: Metrics.monthY)
                }
                .frame(width: width, height: Metrics.height)
                .clipped()
                .contentShape(Rectangle())
                .gesture(drag(width: width))
            }
            .frame(width: width, height: Metrics.height)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .frame(height: Metrics.height)
        .onAppear { reset() }
        .onChange(of: cards.count) { reset() }
        .task(id: LoopKey(auto: auto, touches: touches)) { await runLoop() }
    }

    private struct LoopKey: Equatable {
        let auto: Bool
        let touches: Int
    }

    // MARK: Parts

    /// One card: its picture, the shade it takes as it turns away, its badge and, in front, its captions.
    private func card(_ i: Int, away: Double, caption: Double) -> some View {
        let item = cards[i]
        return ZStack {
            CylinderMonthCarouselArt(scene: item.scene)
                .equatable()
            Color.black.opacity(Metrics.shade * min(1, away))

            Image(systemName: item.locked ? "lock.fill" : "heart")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 34, height: 34)
                .background(Circle().fill(.black.opacity(0.45)))
                .position(x: Metrics.cardW - 29, y: 29)

            Text(item.title)
                .font(.system(size: 20, weight: .medium))
                .tracking(1.5)
                .foregroundStyle(.white)
                .shadow(color: .black.opacity(0.35), radius: 6, x: 0, y: 2)
                .position(x: Metrics.cardW / 2, y: Metrics.cardH / 3 + (1 - caption) * Metrics.captionRise)
                .opacity(caption)
            Text(item.artist)
                .font(.system(size: 11))
                .foregroundStyle(.white)
                .padding(.horizontal, 12)
                .frame(height: 28)
                .background(Capsule().fill(.black.opacity(0.45)))
                .position(x: Metrics.cardW / 2, y: Metrics.cardH - 40 + (1 - caption) * Metrics.captionRise)
                .opacity(caption)
        }
        .lineLimit(1)
        .frame(width: Metrics.cardW, height: Metrics.cardH)
        .clipShape(RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    /// The glow the front card casts, and the ticks over it.
    private func drawFloor(_ context: GraphicsContext, size: CGSize, p: Double) {
        let middle = size.width / 2
        let glow = tone(p)
        context.fillEllipse(
            center: CGPoint(x: middle, y: Metrics.glowY),
            radii: CGSize(width: Metrics.glowW, height: Metrics.glowH),
            gradient: Gradient(colors: [glow.opacity(0.55), glow.opacity(0.2), glow.opacity(0)])
        )
        for i in cards.indices {
            let d = Double(i) - p
            // The tick under the front card is white, thicker and taller; it hands over half-way.
            let lit = clamp01((0.5 - abs(d)) / Metrics.handOver + 0.5)
            let width = mix(2, 3, lit)
            let height = mix(20, 26, lit)
            let x = middle + Metrics.tickGap * Metrics.tickBunch * tanh(d / Metrics.tickBunch)
            let tick = CGRect(x: x - width / 2, y: Metrics.ticksY - height / 2, width: width, height: height)
            context.fill(Path(roundedRect: tick, cornerRadius: 1), with: .color(.white.opacity(mix(0.35, 1, lit))))
        }
    }

    /// The glow colour of the card at `p`, cross-faded with its neighbour's in between.
    private func tone(_ p: Double) -> Color {
        guard cards.count > 1 else { return Color(hex: cards.first?.glow ?? 0) }
        let i = min(max(Int(p.rounded(.down)), 0), cards.count - 2)
        return blend(cards[i].glow, cards[i + 1].glow, clamp01(p - Double(i)))
    }

    // MARK: The drum's moves

    private func drag(width: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                if !motion.dragging {
                    // The finger catches the drum wherever it is.
                    touches += 1
                    motion.dragging = true
                    motion.dragged = false
                    motion.felt = true
                    motion.dragFrom = motion.pos.value(at: .now)
                    motion.pos.set(motion.dragFrom)
                }
                guard motion.dragged || abs(value.translation.width) > Metrics.tapSlop else { return }
                if !motion.dragged {
                    motion.dragged = true
                    motion.moving = true
                    motion.caption.retarget(0, .tween(Metrics.captionOut, .linear))
                }
                motion.pos.set(banded(motion.dragFrom - value.translation.width / Metrics.dragStep))
            }
            .onEnded { value in
                touches += 1
                motion.dragging = false
                let p = motion.pos.value(at: .now)
                let speed = -value.velocity.width / Metrics.dragStep
                if motion.dragged {
                    // The drum keeps the finger's speed and settles on the card it would coast to.
                    select(Int((p + speed * Metrics.coast).rounded()), velocity: speed)
                } else if value.location.y < Metrics.cardH, abs(value.location.x - width / 2) > Metrics.cardW / 2 {
                    // A tap on a card at the side brings it to the front.
                    select(Int(p.rounded()) + (value.location.x > width / 2 ? 1 : -1))
                } else {
                    select(Int(p.rounded()))
                }
            }
    }

    /// Past either end the drum follows the finger at a third of its speed.
    private func banded(_ p: Double) -> Double {
        let last = Double(cards.count - 1)
        if p < 0 { return p * Metrics.band }
        if p > last { return last + (p - last) * Metrics.band }
        return p
    }

    private func reset() {
        motion.count = cards.count
        motion.target = home
        motion.captioned = home
        motion.ticked = home
        motion.pos = Track(Double(home))
        motion.caption = Track(1)
        motion.moving = false
    }

    /// Sends the drum to card `index`: on a spring that is softer the further it has to turn, or, for
    /// the script's long way home, on an ease-in-out spin that overshoots a little.
    private func select(_ index: Int, velocity: Double = 0, spin: Bool = false) {
        guard !cards.isEmpty else { return }
        let index = min(max(index, 0), cards.count - 1)
        let far = abs(Double(index) - motion.pos.value(at: .now))
        if far > Metrics.landWithin { motion.caption.retarget(0, .tween(Metrics.captionOut, .linear)) }
        let changed = index != motion.target
        motion.target = index
        motion.moving = true
        if reduceMotion {
            motion.pos.set(Double(index))
        } else if spin {
            motion.pos.retarget(Double(index), .tween(Metrics.spinTime, spinCurve))
        } else {
            let stiffness = Metrics.stiffness / pow(max(1, far), 0.75)
            motion.pos.retarget(Double(index), .spring(Spring(mass: 1, stiffness: stiffness, damping: Metrics.dampingRatio * 2 * stiffness.squareRoot())), velocity: velocity)
        }
        if changed { onSelect?(index) }
    }

    /// The script a finger would follow: a turn, a fling, a turn, and a long spin home.
    private func runLoop() async {
        guard auto else { return }
        do {
            if touches > 0 {
                // A touch took over: wait, go back to the resting card, and start over from there.
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
                    select(home + step.by, spin: step.spin)
                }
                start += .seconds(Metrics.loop)
            }
        } catch {}
    }
}

/// The picture on a card. It never changes, so it is drawn once and only moved afterwards.
private struct CylinderMonthCarouselArt: View, Equatable {
    let scene: CylinderMonthCarouselScene

    var body: some View {
        Canvas { context, size in
            let w = size.width
            let h = size.height
            let seed = scene.seed
            context.fill(
                Path(CGRect(origin: .zero, size: size)),
                with: .linearGradient(Gradient(colors: [Color(hex: scene.sky), Color(hex: scene.haze)]), startPoint: .zero, endPoint: CGPoint(x: 0, y: h * 0.62))
            )
            let sun = CGPoint(x: w * scene.sunX, y: h * (0.16 + 0.04 * sin(seed * 5)))
            context.fill(Path(ellipseIn: CGRect(x: sun.x - 62, y: sun.y - 62, width: 124, height: 124)), with: .color(Color(hex: scene.sun, opacity: 0.22)))
            context.fill(Path(ellipseIn: CGRect(x: sun.x - 30, y: sun.y - 30, width: 60, height: 60)), with: .color(Color(hex: scene.sun)))

            context.fill(ridge(w, h, level: 0.5, swell: 0.05, waves: 1.1, turn: seed), with: .color(Color(hex: scene.far)))
            context.fill(ridge(w, h, level: 0.62, swell: 0.06, waves: 0.8, turn: seed * 2.3 + 1), with: .color(Color(hex: scene.mid)))
            for k in 0..<6 {
                let u = (Double(k) + 0.5 + 0.35 * sin(seed * 7 + Double(k) * 2.4)) / 6
                let x = u * w
                let base = h * (0.62 + 0.06 * sin(u * tau * 0.8 + seed * 2.3 + 1)) + 8
                let tall = 44 + 26 * (0.5 + 0.5 * sin(seed * 3 + Double(k) * 1.7))
                if k % 2 == 0 {
                    context.fill(Path(CGRect(x: x - 1.5, y: base - tall * 0.5, width: 3, height: tall * 0.5)), with: .color(Color(hex: scene.pine)))
                    context.fill(Path(ellipseIn: CGRect(x: x - tall * 0.3, y: base - tall, width: tall * 0.6, height: tall * 0.78)), with: .color(Color(hex: scene.tree)))
                } else {
                    var pine = Path()
                    pine.move(to: CGPoint(x: x - tall * 0.24, y: base))
                    pine.addLine(to: CGPoint(x: x, y: base - tall * 1.15))
                    pine.addLine(to: CGPoint(x: x + tall * 0.24, y: base))
                    pine.closeSubpath()
                    context.fill(pine, with: .color(Color(hex: scene.pine)))
                }
            }
            context.fill(ridge(w, h, level: 0.8, swell: 0.035, waves: 0.6, turn: seed * 3.7 + 2), with: .color(Color(hex: scene.near)))
            // The bottom darkens so the artist's pill reads on any picture.
            context.fill(
                Path(CGRect(x: 0, y: h * 0.6, width: w, height: h * 0.4)),
                with: .linearGradient(Gradient(colors: [.black.opacity(0), .black.opacity(0.4)]), startPoint: CGPoint(x: 0, y: h * 0.6), endPoint: CGPoint(x: 0, y: h))
            )
        }
    }

    /// A range of hills: a sine wave across the card at the height `level`, filled down to the bottom.
    private func ridge(_ w: Double, _ h: Double, level: Double, swell: Double, waves: Double, turn: Double) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 0, y: h))
        for step in 0...24 {
            let u = Double(step) / 24
            path.addLine(to: CGPoint(x: u * w, y: h * (level + swell * sin(u * tau * waves + turn))))
        }
        path.addLine(to: CGPoint(x: w, y: h))
        path.closeSubpath()
        return path
    }
}

/// Where a card `d` places from the front stands: on a drum of radius `Metrics.radius`, seen from
/// `Metrics.eye` points away, so it moves sideways less and shrinks as it goes round.
private func cylinderMonthCarouselPlace(_ d: Double) -> (x: Double, scale: Double) {
    let angle = d * Metrics.turn * .pi / 180
    let scale = Metrics.eye / (Metrics.eye + Metrics.radius * (1 - cos(angle)))
    return (Metrics.radius * sin(angle) * scale, scale)
}

private enum Metrics {
    static let maxWidth = 430.0
    static let height = 510.0

    // The cards and the drum they stand on.
    static let cardW = 225.0
    static let cardH = 400.0
    static let cardRadius = 28.0
    static let radius = 210.0
    /// Degrees between two cards.
    static let turn = 56.0
    static let eye = 800.0
    /// How dark a card one place from the front is.
    static let shade = 0.4
    static let captionRise = 8.0

    // The ticks, the month and the glow.
    static let ticksY = 436.0
    static let tickGap = 18.0
    /// How many places from the front the ticks start to bunch.
    static let tickBunch = 4.5
    static let handOver = 0.15
    static let monthY = 482.0
    static let glowY = 448.0
    static let glowW = 200.0
    static let glowH = 62.0

    // Turning.
    /// Points of drag that turn the drum by one card.
    static let dragStep = 205.0
    /// Seconds of the finger's speed the drum coasts for.
    static let coast = 0.2
    static let stiffness = 180.0
    static let dampingRatio = 0.63
    static let spinTime = 1.2
    static let band = 0.35
    static let tapSlop = 6.0
    /// The drum has landed once it is this close (in cards) to its target.
    static let landWithin = 0.03
    static let captionIn = 0.25
    static let captionOut = 0.12

    // autoPlay (seconds): when each turn starts, and how many cards from the resting one it goes to.
    static let loop = 8.0
    static let script: [(at: Double, by: Int, spin: Bool)] = [
        (1.0, 1, false),
        (2.3, 4, false),
        (4.1, 5, false),
        (5.3, 0, true),
    ]
    /// After a touch the loop waits this long, then takes `homeTime` to return to the resting card.
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

/// Ease-in-out that runs a little past the end and comes back.
private let spinCurve = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.5, y: 0), endControlPoint: UnitPoint(x: 0.3, y: 1.1))

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

private func blend(_ a: UInt32, _ b: UInt32, _ t: Double) -> Color {
    func channel(_ shift: UInt32) -> Double {
        mix(Double((a >> shift) & 0xff), Double((b >> shift) & 0xff), t) / 255
    }
    return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0), opacity: 1)
}

#Preview {
    ZStack {
        Color(hex: 0x1b1b1d).ignoresSafeArea()
        CylinderMonthCarousel(autoPlay: true)
    }
}
