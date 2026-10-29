import SwiftUI
import UIKit

/// The step-rewards screen of an invented neighbourhood-walk app. Bao, a round steamed-bun buddy
/// with a tote and a baguette, walks without end while the street scrolls by in layers: clouds drift
/// and dissolve, hills and trees slide slowly, the bench, the walk sign, the planter and the grass
/// pass fast. Every footfall adds a chunk to today's count (it rolls like a drum) and the goal bar
/// advances; passing 1,000 steps pops confetti, a check badge and a "+10 coins" bubble. The sun
/// sets, then rises again as the count rolls back to 0: a fresh day, and the loop starts over.
///
/// Drag sideways to walk Bao yourself (back and forth, the world and the legs move together); on
/// release the walk carries on at its own pace. Tap Bao for a hop. Reduced motion: the rest frame.
struct WalkingStepsBuddy: View {
    static let loop = WalkingStepsBuddyMotion.loop

    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date()
    @State private var touch = WalkingStepsBuddyTouch()
    @State private var awake = false

    private typealias M = WalkingStepsBuddyMotion
    private typealias Art = WalkingStepsBuddyArt

    var body: some View {
        GeometryReader { outer in
            let safeTop = outer.safeAreaInsets.top
            GeometryReader { geometry in
                let k = geometry.size.width / 393
                let height = geometry.size.height / k
                let top = Double(safeTop / k)
                let g = M.ground(safeTop: top)
                TimelineView(.animation(paused: reducedMotion || scenePhase != .active || !(autoPlay || awake))) { timeline in
                    let clock = max(0, timeline.date.timeIntervalSince(epoch))
                    screen(clock: clock, k: k, height: height, top: top, g: g)
                }
                .contentShape(Rectangle())
                .gesture(drag(k: k, g: g))
            }
            .ignoresSafeArea()
        }
        .background(Art.color(Art.page).ignoresSafeArea())
        .preferredColorScheme(.light)
        .task(id: awake) {
            guard awake, !autoPlay else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(250))
                let clock = Date().timeIntervalSince(epoch)
                if !touch.touching, clock > touch.restsAfter {
                    awake = false
                    return
                }
            }
        }
    }

    // MARK: Screen

    @ViewBuilder
    private func screen(clock: Double, k: CGFloat, height: Double, top: Double, g: Double) -> some View {
        let still = reducedMotion
        let t = still ? 0 : touch.loopTime(clock, autoPlay: autoPlay)
        let d = still ? 0 : touch.distance(clock, autoPlay: autoPlay)
        let hop = still ? (lift: 0.0, squash: 1.0) : M.hop(clock - touch.hopClock)
        let s = g + 38
        let phase = M.countPhase(t)
        let steps = M.steps(phase: phase)
        let happy = !still && (M.happy(t) || clock - touch.hopClock < 0.75)
        ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                context.scaleBy(x: k, y: k)
                var pose = M.pose(distance: d)
                pose.lift = hop.lift
                pose.sy *= hop.squash
                pose.sx *= 2 - hop.squash
                Art.drawScene(context, distance: d, t: t, ground: g, pose: pose, happy: happy)
                Art.drawBar(context, t: t, steps: steps, top: s + 10)
            }
            .allowsHitTesting(false)
            .accessibilityElement()
            .accessibilityLabel("Bao walking down a sunny street")

            ZStack(alignment: .topLeading) {
                nav.frame(width: 393, height: 44).position(x: 196.5, y: top + 22)
                Text("Steps today")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Art.color(Art.label))
                    .frame(width: 200, alignment: .leading)
                    .position(x: 20 + 100, y: top + 68)
                    .accessibilityHidden(true)
                Canvas { context, _ in Art.drawShoe(context) }
                    .frame(width: 36, height: 28)
                    .position(x: 20 + 18, y: top + 106)
                    .accessibilityHidden(true)
                WalkingStepsBuddyDrum(items: M.countStrings, phase: phase, row: 52,
                                      font: .system(size: 44, weight: .heavy).monospacedDigit(), color: Art.color(Art.ink))
                    .position(x: 62 + 110, y: top + 106)
                Color.clear
                    .frame(width: 240, height: 70)
                    .position(x: 20 + 120, y: top + 92)
                    .accessibilityElement()
                    .accessibilityLabel("Steps today, \(M.countStrings[min(Int(phase.rounded()), M.counts.count - 1)])")
                milestones(t: t, s: s)
                card.frame(width: 361).position(x: 196.5, y: s + 116 + 113)
            }
            .frame(width: 393, height: height, alignment: .topLeading)
            .scaleEffect(k, anchor: .topLeading)

            Canvas { context, _ in
                context.scaleBy(x: k, y: k)
                Art.drawCelebration(context, t: t, barCentre: s + 16)
            }
            .allowsHitTesting(false)
            .accessibilityHidden(true)
        }
    }

    private var nav: some View {
        ZStack {
            Text("Neighborhood walk")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Art.color(Art.ink))
                .accessibilityAddTraits(.isHeader)
            HStack(spacing: 0) {
                Canvas { context, _ in Art.drawChevron(context) }
                    .frame(width: 12, height: 18)
                    .frame(width: 44, height: 44)
                    .accessibilityLabel("Back")
                Spacer()
                Canvas { context, _ in Art.drawShare(context) }
                    .frame(width: 22, height: 22)
                    .frame(width: 38, height: 44)
                    .accessibilityLabel("Share")
                Canvas { context, _ in Art.drawGear(context) }
                    .frame(width: 22, height: 22)
                    .frame(width: 38, height: 44)
                    .accessibilityLabel("Settings")
            }
            .padding(.leading, 8)
            .padding(.trailing, 10)
        }
    }

    private func milestones(t: Double, s: Double) -> some View {
        ZStack(alignment: .topLeading) {
            ForEach(0..<4, id: \.self) { i in
                let mix = M.pillMix(i, t)
                Text(M.milestoneLabels[i])
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Art.mix(Art.muted, Art.ink, mix))
                    .fixedSize()
                    .position(x: M.tickX[i], y: s + 44)
                HStack(spacing: 5) {
                    Canvas { context, _ in Art.drawCoin(context, CGPoint(x: 7, y: 7), 7, mixF: mix) }
                        .frame(width: 14, height: 14)
                    Text(M.rewards[i])
                        .font(.system(size: 13, weight: .bold).monospacedDigit())
                        .foregroundStyle(Art.mix(0xB4BCC6, 0xE07A12, mix))
                        .fixedSize()
                }
                .frame(width: 72, height: 30)
                .background(Capsule().fill(Art.mix(0xF1F3F5, 0xFFEFD2, mix)))
                .scaleEffect(1 + 0.08 * sin(Double.pi * M.clamp(mix)))
                .position(x: M.tickX[i], y: s + 76)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(M.milestoneLabels[i]), \(M.rewards[i]) coins, \(mix > 0.5 ? "reached" : "not reached")")
            }
        }
        .frame(width: 393, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    private var card: some View {
        VStack(spacing: 0) {
            row(icon: Art.drawCalendar, title: "Check in every day", detail: "1 coin a day", claim: true)
            row(icon: Art.drawGift, title: "Open the lucky box", detail: "Up to 200 coins a day", claim: false)
            row(icon: Art.drawPlane, title: "Walk with a friend", detail: "Up to 1,000 coins each", claim: false)
        }
        .padding(.vertical, 8)
        .frame(width: 361)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(Color.white)
                .shadow(color: Art.color(0x3A4A5C, 0.05), radius: 1.5, y: 1)
                .shadow(color: Art.color(0x3A4A5C, 0.08), radius: 16, y: 8)
        )
    }

    private func row(icon: @escaping (GraphicsContext) -> Void, title: String, detail: String, claim: Bool) -> some View {
        HStack(spacing: 14) {
            Canvas { context, _ in icon(context) }
                .frame(width: 44, height: 44)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Art.color(Art.ink))
                Text(detail)
                    .font(.system(size: 13))
                    .foregroundStyle(Art.color(Art.muted))
            }
            .lineLimit(1)
            Spacer(minLength: 0)
            if claim {
                HStack(spacing: 5) {
                    Canvas { context, _ in Art.drawCoin(context, CGPoint(x: 8, y: 8), 8, mixF: 1) }
                        .frame(width: 16, height: 16)
                    Text("Claim")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Art.color(Art.ink))
                }
                .padding(.horizontal, 12)
                .frame(height: 32)
                .background(Capsule().fill(Art.color(0xF3F4F6)))
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Claim")
                .accessibilityAddTraits(.isButton)
            }
        }
        .padding(.horizontal, 18)
        .frame(height: 70)
        .accessibilityElement(children: .combine)
    }

    // MARK: Touch

    private func drag(k: CGFloat, g: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                guard Double(value.startLocation.y / k) < g + 40 else { return }
                if reducedMotion { return }
                let clock = Date().timeIntervalSince(epoch)
                if !touch.touching {
                    touch.begin(clock: clock, autoPlay: autoPlay)
                    if !awake { awake = true }
                }
                touch.move(dx: value.translation.width / k, dy: value.translation.height / k)
            }
            .onEnded { value in
                guard touch.touching else {
                    if reducedMotion, Double(value.startLocation.y / k) < g + 40 {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    }
                    return
                }
                let clock = Date().timeIntervalSince(epoch)
                let point = CGPoint(x: value.location.x / k, y: value.location.y / k)
                if touch.end(clock: clock, velocityX: value.velocity.width / k, point: point, ground: g, autoPlay: autoPlay) {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                }
            }
    }
}

/// The count on a drum: rows sit `row` pt apart and roll up with the phase, fading away from the middle.
struct WalkingStepsBuddyDrum: View {
    let items: [String]
    let phase: Double
    let row: CGFloat
    let font: Font
    let color: Color

    var body: some View {
        let base = Int(floor(phase))
        let frac = phase - Double(base)
        ZStack(alignment: .leading) {
            ForEach(0...1, id: \.self) { o in
                let j = base + o
                if j >= 0 && j < items.count {
                    let d = Double(j) - phase
                    Text(items[j])
                        .font(font)
                        .foregroundStyle(color)
                        .lineLimit(1)
                        .fixedSize()
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
                        .offset(y: d * row)
                        .opacity(max(0, 1 - 1.6 * abs(d)))
                }
            }
        }
        .scaleEffect(1 + 0.05 * sin(Double.pi * frac), anchor: .leading)
        .frame(width: 220, height: row)
        .clipped()
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
