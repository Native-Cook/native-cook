import SwiftUI
import UIKit

/// The product page of an invented body oil (Isla Bloom, Golden Pineapple) on a sunny yellow
/// backdrop. At rest a whole pineapple stands closed; its crowned top half lifts away on a spring,
/// the amber bottle rises out of the fruit and hovers between the halves while a ring of juice drops
/// bursts off the cut rim and the hibiscus flowers drift; then everything settles back.
///
/// Loop (7 s): rest → press and lift (1.0–2.4 s) → the bottle rises (1.3 s) in a burst of drops
/// (1.45 s) → hovers → sinks back (4.6–5.5 s) → the top closes and lands (5.75 s) → rest. Drag up to
/// lift it yourself, release to let it spring shut; tap for a hop and a splash. Reduced motion: the
/// open product shot, still.
struct PineappleLiftProduct: View {
    static let loop = PineappleLiftProductMotion.loop

    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date()
    @State private var touch = PineappleLiftProductTouch()
    @State private var awake = false
    @State private var pressStart: Date?

    private typealias M = PineappleLiftProductMotion
    private typealias Art = PineappleLiftProductArt

    var body: some View {
        GeometryReader { outer in
            let safeTop = outer.safeAreaInsets.top
            let safeBottom = outer.safeAreaInsets.bottom
            GeometryReader { geometry in
                let k = geometry.size.width / 393
                let layout = Art.Layout(height: Double(geometry.size.height / k), top: Double(safeTop / k),
                                        bottom: Double(safeBottom / k))
                TimelineView(.animation(paused: reducedMotion || scenePhase != .active || !(autoPlay || awake))) { timeline in
                    let clock = max(0, timeline.date.timeIntervalSince(epoch))
                    screen(clock: clock, k: k, layout: layout)
                }
            }
            .ignoresSafeArea()
        }
        .background(Art.color(Art.skyMid).ignoresSafeArea())
        .preferredColorScheme(.light)
        .task(id: awake) {
            guard awake, !autoPlay else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(250))
                if !touch.touching, Date().timeIntervalSince(epoch) > touch.restsAfter + 0.1 {
                    awake = false
                    return
                }
            }
        }
    }

    // MARK: Screen

    @ViewBuilder
    private func screen(clock: Double, k: CGFloat, layout l: Art.Layout) -> some View {
        let still = reducedMotion
        let _ = still ? () : haptics(clock)
        let frame: M.Frame = still ? M.Frame(open: 1, rise: 1, glow: 1) : touch.frame(clock, autoPlay: autoPlay)
        let ambient = still ? 0 : M.mod(clock, M.loop)

        ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                context.scaleBy(x: k, y: k)
                Art.drawBackground(context, l)
                Art.drawFlowers(context, l, drift: frame.drift, ambient: ambient)
                Art.drawHero(context, l, frame: frame, ambient: ambient)
                Art.drawDrops(context, l, frame: frame)
                Art.drawBag(context, center: CGPoint(x: 349, y: l.barY))
            }
            .allowsHitTesting(false)
            .accessibilityElement()
            .accessibilityLabel("A pineapple cut in half, the top lifting away as an amber bottle of body oil rises out of it")

            ZStack(alignment: .topLeading) {
                Color.clear
                    .frame(width: 393, height: l.ctaTop - 8)
                    .contentShape(Rectangle())
                    .gesture(drag())
                    .accessibilityAddTraits(.isButton)
                    .accessibilityLabel("Open the pineapple")
                    .accessibilityHint("Drag up to lift the bottle out, tap for a splash")
                    .accessibilityAction { tap() }
                texts(l)
            }
            .frame(width: 393, height: l.height, alignment: .topLeading)
            .scaleEffect(k, anchor: .topLeading)
        }
    }

    // MARK: Copy

    private func texts(_ l: Art.Layout) -> some View {
        ZStack(alignment: .topLeading) {
            Text("ISLA BLOOM")
                .font(.system(size: 13, weight: .semibold))
                .tracking(3)
                .foregroundStyle(Art.color(Art.ink2))
                .fixedSize()
                .frame(width: 200, alignment: .leading)
                .position(x: 24 + 100, y: l.barY)
                .accessibilityAddTraits(.isHeader)
            Text("1")
                .font(.system(size: 10, weight: .bold))
                .foregroundStyle(Art.color(Art.cream))
                .position(x: 349 + 14, y: l.barY - 8)
                .accessibilityLabel("1 item in bag")

            Group {
                Text("BODY OIL  ·  NO. 07")
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(2.2)
                    .foregroundStyle(Art.color(Art.ink2, 0.65))
                    .fixedSize()
                    .frame(width: 345, alignment: .leading)
                    .position(x: 24 + 172.5, y: l.captionY)
                Text("Golden Pineapple")
                    .font(.custom("Georgia", fixedSize: 34))
                    .foregroundStyle(Art.color(Art.ink))
                    .fixedSize()
                    .frame(width: 345, alignment: .leading)
                    .position(x: 24 + 172.5, y: l.titleY)
                    .accessibilityAddTraits(.isHeader)
                Text("Cold-pressed pineapple & coconut for a sunlit glow.")
                    .font(.system(size: 14))
                    .foregroundStyle(Art.color(Art.ink2, 0.72))
                    .fixedSize()
                    .frame(width: 345, alignment: .leading)
                    .position(x: 24 + 172.5, y: l.descY)
            }

            Text("$28")
                .font(.system(size: 26, weight: .bold))
                .foregroundStyle(Art.color(Art.ink))
                .fixedSize()
                .frame(width: 120, alignment: .leading)
                .position(x: 24 + 60, y: l.ctaTop + 19)
            Text("100 ml")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Art.color(Art.ink2, 0.6))
                .fixedSize()
                .frame(width: 120, alignment: .leading)
                .position(x: 24 + 60, y: l.ctaTop + 43)

            Button {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            } label: {
                Text("Add to bag")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Art.color(Art.cream))
                    .frame(width: 200, height: 56)
                    .background(Capsule().fill(Art.color(Art.ink)))
            }
            .buttonStyle(.plain)
            .position(x: 369 - 100, y: l.ctaTop + 28)
        }
        .frame(width: 393, height: l.height, alignment: .topLeading)
    }

    // MARK: Touch

    private func haptics(_ clock: Double) {
        guard touch.takeHaptic(clock) else { return }
        DispatchQueue.main.async { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
    }

    private func tap() {
        if reducedMotion { return }
        let clock = Date().timeIntervalSince(epoch)
        touch.begin(clock, autoPlay: autoPlay)
        touch.end(clock, tapped: true)
        if !awake { awake = true }
    }

    private func drag() -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                if reducedMotion { return }
                let clock = Date().timeIntervalSince(epoch)
                if pressStart == nil {
                    pressStart = Date()
                    touch.begin(clock, autoPlay: autoPlay)
                    if !awake { awake = true }
                }
                touch.drag(clock, dy: Double(value.translation.height))
            }
            .onEnded { value in
                guard let start = pressStart else { return }
                pressStart = nil
                let moved = hypot(value.translation.width, value.translation.height)
                let tapped = Date().timeIntervalSince(start) < 0.25 && moved < 10
                touch.end(Date().timeIntervalSince(epoch), tapped: tapped)
            }
    }
}
