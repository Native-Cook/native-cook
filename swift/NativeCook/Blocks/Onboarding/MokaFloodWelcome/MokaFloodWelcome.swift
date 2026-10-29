import SwiftUI
import UIKit

/// The first onboarding step of an invented morning-routine app, Brewday, as a flat illustration
/// on peach: a crimson moka pot tilts and pours into a faceted glass, the coffee rises with a
/// wobbling meniscus, domes over the rim, spills down the walls and floods the whole screen from
/// the bottom; the welcome is written on the coffee in cream handwriting, then the flood drains.
///
/// Loop (9.5 s): rest → the pot tilts (1.0 s) and pours (1.2) → the glass fills (1.72–3.6) → domes
/// and spills (3.6–4.7) → the flood rises (4.7–6.3) as the pot rights itself and the stream breaks
/// (5.15–5.4) → the title is written (6.35–7.35) → the flood drains (8.05–8.95) → rest. Hold to
/// pour, drag left or down to pour harder; the loop resumes 2 s after the last touch. Reduced
/// motion: the coffee page with its copy, still.
struct MokaFloodWelcome: View {
    static let loop = MokaFloodWelcomeMotion.loop

    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date()
    @State private var touch = MokaFloodWelcomeTouch()
    @State private var awake = false
    @State private var pressed = false

    private typealias M = MokaFloodWelcomeMotion
    private typealias Art = MokaFloodWelcomeArt

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
        .background(Art.color(Art.peach).ignoresSafeArea())
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

    @ViewBuilder
    private func screen(clock: Double, k: CGFloat, layout l: Art.Layout) -> some View {
        let still = reducedMotion
        let _ = still ? () : haptics(clock)
        let frame: M.Frame = still ? M.Frame(volume: 3, title: 1, sub: 1) : touch.frame(clock, autoPlay: autoPlay)

        ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                context.scaleBy(x: k, y: k)
                Art.draw(context, l, frame)
            }
            .allowsHitTesting(false)
            .accessibilityElement()
            .accessibilityLabel("Brewday. Brew your morning routine! Brewday lines up your first cup, your stretch and the plan for today.")

            ZStack(alignment: .topLeading) {
                Color.clear
                    .frame(width: 393, height: l.ctaTop - 12)
                    .contentShape(Rectangle())
                    .gesture(pour())
                    .accessibilityAddTraits(.isButton)
                    .accessibilityLabel("Moka pot")
                    .accessibilityHint("Hold to pour coffee into the glass")
                Color.clear
                    .frame(width: 345, height: 56)
                    .contentShape(Capsule())
                    .onTapGesture { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
                    .accessibilityAddTraits(.isButton)
                    .accessibilityLabel("Get started")
                    .offset(x: 24, y: l.ctaTop)
            }
            .frame(width: 393, height: l.height, alignment: .topLeading)
            .scaleEffect(k, anchor: .topLeading)
        }
    }

    private func haptics(_ clock: Double) {
        guard touch.takeHaptic(clock) else { return }
        DispatchQueue.main.async { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
    }

    private func pour() -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                if reducedMotion { return }
                let clock = Date().timeIntervalSince(epoch)
                if !pressed {
                    pressed = true
                    touch.begin(clock, autoPlay: autoPlay)
                    if !awake { awake = true }
                }
                touch.move(clock, dx: Double(value.translation.width), dy: Double(value.translation.height))
            }
            .onEnded { _ in
                guard pressed else { return }
                pressed = false
                touch.end(Date().timeIntervalSince(epoch))
            }
    }
}
