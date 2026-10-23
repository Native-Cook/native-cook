import SwiftUI

/// A mood journal: a glass jar with a metal lid fills with glossy face orbs. Flick an orb up from the
/// five-mood picker and it flies into the jar on an arc solved for the mouth; swipe the new entry to
/// undo. Ported from `react-native/src/blocks/gestures/mood-orb-jar`.
struct MoodOrbJar: View {
    /// The autoplay period, in seconds (motion.ts LOOP_MS = 12 000).
    static let loop: Double = MoodOrbJarTokens.Motion.loopMS / 1000

    @State private var controller: MoodOrbJarController
    @State private var isShown = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(autoPlay: Bool = false) {
        _controller = State(initialValue: MoodOrbJarController(autoPlay: autoPlay))
    }

    var body: some View {
        GeometryReader { safeAreaProxy in
            let safeTop = safeAreaProxy.safeAreaInsets.top
            GeometryReader { proxy in
                let size = proxy.size
                let geometry = MoodOrbJarGeometry(width: size.width, height: size.height, safeTop: safeTop)
                screen(geometry)
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                    .onAppear {
                        MoodOrbJarAssets.assertAllLoaded()
                        assert(Self.loop * 1000 == MoodOrbJarTokens.Motion.loopMS, "loop and LOOP_MS disagree")
                        controller.configure(
                            width: size.width,
                            height: size.height,
                            safeTop: safeTop,
                            reducedMotion: reduceMotion
                        )
                        controller.start()
                        withAnimation(.timingCurve(0.455, 0.03, 0.515, 0.955, duration: MoodOrbJarTokens.Motion.rowEnter)) {
                            isShown = true
                        }
                    }
                    .onDisappear(perform: controller.stop)
                    .onChange(of: geometry) { _, next in
                        controller.configure(
                            width: next.width,
                            height: next.height,
                            safeTop: next.safeTop,
                            reducedMotion: reduceMotion
                        )
                    }
                    .onChange(of: reduceMotion) { _, next in
                        controller.configure(width: size.width, height: size.height, safeTop: safeTop, reducedMotion: next)
                    }
            }
            .ignoresSafeArea()
        }
        .background(MoodOrbJarTokens.Palette.background.ignoresSafeArea())
        .preferredColorScheme(.light)
    }

    private func screen(_ geometry: MoodOrbJarGeometry) -> some View {
        ZStack(alignment: .topLeading) {
            MoodOrbJarSceneLayer(controller: controller, geometry: geometry)
            MoodOrbJarHeader(geometry: geometry)
            MoodOrbJarPickerLabels(controller: controller, geometry: geometry)
            MoodOrbJarJarLabel(controller: controller, geometry: geometry)
            MoodOrbJarList(controller: controller, geometry: geometry, isShown: isShown)
            // The shared element flies above the list, so it lands on the new card's avatar.
            MoodOrbJarGhostLayer(controller: controller, geometry: geometry)
            // Takes the touches that start on a picker orb or on the newest entry; the rest pass through.
            MoodOrbJarTouchView(controller: controller)
        }
    }
}
