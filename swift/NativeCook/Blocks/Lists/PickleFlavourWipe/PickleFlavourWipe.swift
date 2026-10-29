import SwiftUI
import UIKit

/// A pantry shop's flavour browser for an invented pickle brand, jarful. Each flavour is a page in
/// its own colour with a jar and its ingredients floating around it; a diagonal slash wipes the next
/// flavour in (Mango → Lime → Garlic), the jar springs up from below, the ingredients pop out from
/// behind it and the big title slides in with a smear. The last page lines the three jars up.
///
/// Loop (10 s): rest on Mango → wipes at 1.0, 3.25, 5.5 s → the Trio line-up → Mango wipes back in
/// at 7.75 s and settles → rest. Swipe left or right to wipe to the next or previous flavour: the
/// edge follows the finger, release completes the wipe or springs it back. Reduced motion: the Mango
/// page, still; swipes cross-fade.
struct PickleFlavourWipe: View {
    static let loop = PickleFlavourWipeMotion.loop

    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date()
    @State private var touch = PickleFlavourWipeTouch()
    @State private var awake = false
    @State private var dragging = false

    private typealias M = PickleFlavourWipeMotion
    private typealias Art = PickleFlavourWipeArt

    var body: some View {
        GeometryReader { outer in
            let safeTop = outer.safeAreaInsets.top
            let safeBottom = outer.safeAreaInsets.bottom
            GeometryReader { geometry in
                let k = geometry.size.width / 393
                let layout = Art.Layout(height: Double(geometry.size.height / k), top: Double(safeTop / k),
                                        bottom: Double(safeBottom / k))
                TimelineView(.animation(paused: scenePhase != .active || !(running || awake))) { timeline in
                    let clock = max(0, timeline.date.timeIntervalSince(epoch))
                    screen(clock: clock, k: k, layout: layout)
                }
            }
            .ignoresSafeArea()
        }
        .background(Art.color(Art.pages[0].mid).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .task(id: awake) {
            guard awake, !running else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(250))
                let clock = Date().timeIntervalSince(epoch)
                if !touch.touching, clock > touch.restsAfter + 0.1, !touch.fading(clock) {
                    awake = false
                    return
                }
            }
        }
    }

    private var running: Bool { autoPlay && !reducedMotion }

    // MARK: Screen

    @ViewBuilder
    private func screen(clock: Double, k: CGFloat, layout l: Art.Layout) -> some View {
        let _ = haptics(clock)
        let scene: M.Scene = reducedMotion ? touch.fadeScene(clock) : touch.scene(clock, autoPlay: autoPlay)
        let ambient = reducedMotion ? 0 : M.mod(clock, M.loop)
        let page = Art.pages[scene.reveal > 0.5 || scene.fade > 0.5 ? scene.to : scene.from]

        ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                context.scaleBy(x: k, y: k)
                Art.drawScene(context, l, scene, ambient: ambient)
                Art.drawChrome(context, l, scene)
            }
            .allowsHitTesting(false)
            .accessibilityElement()
            .accessibilityLabel("\(page.title) \(page.subtitle): a jar of pickle with its ingredients. \(page.line). \(page.price), \(page.weight)")

            ZStack(alignment: .topLeading) {
                Color.clear
                    .frame(width: 393, height: l.ctaTop - 8)
                    .contentShape(Rectangle())
                    .gesture(drag())
                    .accessibilityElement()
                    .accessibilityLabel("Flavours")
                    .accessibilityAdjustableAction { direction in
                        step(direction == .increment ? -1 : 1)
                    }
                Button {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                } label: {
                    Color.clear.frame(width: 190, height: 56).contentShape(Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(page.cta)
                .position(x: 179 + 95, y: l.ctaTop + 28)
            }
            .frame(width: 393, height: l.height, alignment: .topLeading)
            .scaleEffect(k, anchor: .topLeading)
        }
    }

    // MARK: Touch

    private func haptics(_ clock: Double) {
        guard touch.takeHaptic(clock) else { return }
        DispatchQueue.main.async { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
    }

    /// One page with the accessibility actions: dx < 0 → next.
    private func step(_ dx: Double) {
        let clock = Date().timeIntervalSince(epoch)
        if reducedMotion {
            touch.fadeSwipe(clock, dx: dx * 100)
        } else {
            touch.begin(clock, autoPlay: autoPlay)
            touch.drag(clock, dx: dx * 120)
            touch.end(clock, vx: dx * 1000)
        }
        if !awake { awake = true }
    }

    private func drag() -> some Gesture {
        DragGesture(minimumDistance: 6)
            .onChanged { value in
                if reducedMotion { return }
                let clock = Date().timeIntervalSince(epoch)
                if !dragging {
                    dragging = true
                    touch.begin(clock, autoPlay: autoPlay)
                    if !awake { awake = true }
                }
                touch.drag(clock, dx: Double(value.translation.width))
            }
            .onEnded { value in
                let clock = Date().timeIntervalSince(epoch)
                if reducedMotion {
                    touch.fadeSwipe(clock, dx: Double(value.translation.width))
                    if !awake { awake = true }
                    return
                }
                guard dragging else { return }
                dragging = false
                touch.end(clock, vx: Double(value.velocity.width))
            }
    }
}
