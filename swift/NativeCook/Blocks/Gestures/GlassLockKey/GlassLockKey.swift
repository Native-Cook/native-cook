import SwiftUI

/// The interactive upgrade built on the approved, registered Blender layers.
/// The fixed-step simulation owns time and gesture state; this view owns only
/// the full-screen coordinate space and SwiftUI lifecycle.
@MainActor
struct GlassLockKey: View {
    /// React Native `G_LOOP_MS / 1000`.
    static let loop: Double = 12

    let autoPlay: Bool

    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @State private var simulation: GlassLockKeySimulation

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _simulation = State(
            initialValue: GlassLockKeySimulation(
                width: 402,
                reducedMotion: false,
                autoPlay: autoPlay
            )
        )
    }

    var body: some View {
        GeometryReader { safeAreaProxy in
            GeometryReader { proxy in
                let size = proxy.size
                let state = simulation.state

                GlassLockKeyScene(
                    state: state,
                    size: size,
                    safeAreaInsets: safeAreaProxy.safeAreaInsets,
                    reducedMotion: reducedMotion,
                    send: simulation.send,
                    command: simulation.command
                )
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .onAppear {
                    GlassLockKeySceneAssets.assertAllLoaded()
                    simulation.update(
                        width: size.width,
                        reducedMotion: reducedMotion,
                        autoPlay: autoPlay
                    )
                    simulation.start()
                }
                .onDisappear(perform: simulation.stop)
                .onChange(of: size) { _, next in
                    simulation.update(
                        width: next.width,
                        reducedMotion: reducedMotion,
                        autoPlay: autoPlay
                    )
                }
            .onChange(of: reducedMotion) { _, next in
                simulation.update(width: size.width, reducedMotion: next, autoPlay: autoPlay)
            }
            .onChange(of: autoPlay) { _, next in
                simulation.update(width: size.width, reducedMotion: reducedMotion, autoPlay: next)
            }
            }
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
    }
}
