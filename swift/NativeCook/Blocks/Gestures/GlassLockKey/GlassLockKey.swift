import SwiftUI

@MainActor
struct GlassLockKey: View {
    static let loop: Double = 10

    let autoPlay: Bool

    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @State private var simulation: GlassLockKeySimulation

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _simulation = State(initialValue: GlassLockKeySimulation(
            width: GlassLockKeyConstants.stageWidth,
            height: 852,
            reducedMotion: false,
            autoPlay: autoPlay
        ))
    }

    var body: some View {
        GeometryReader { safeAreaProxy in
            GeometryReader { proxy in
                GlassLockKeyScene(
                    state: simulation.state,
                    geometry: simulation.geometry,
                    size: proxy.size,
                    safeAreaInsets: safeAreaProxy.safeAreaInsets,
                    reducedMotion: reducedMotion,
                    send: simulation.send,
                    command: simulation.command
                )
                .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
                .onAppear {
                    GlassLockKeyAssets.registerFonts()
                    simulation.update(
                        width: proxy.size.width,
                        height: proxy.size.height,
                        reducedMotion: reducedMotion,
                        autoPlay: autoPlay
                    )
                    simulation.start()
                }
                .onDisappear(perform: simulation.stop)
                .onChange(of: proxy.size) { _, size in
                    simulation.update(
                        width: size.width,
                        height: size.height,
                        reducedMotion: reducedMotion,
                        autoPlay: autoPlay
                    )
                }
                .onChange(of: reducedMotion) { _, reduced in
                    simulation.update(
                        width: proxy.size.width,
                        height: proxy.size.height,
                        reducedMotion: reduced,
                        autoPlay: autoPlay
                    )
                }
                .onChange(of: autoPlay) { _, playing in
                    simulation.update(
                        width: proxy.size.width,
                        height: proxy.size.height,
                        reducedMotion: reducedMotion,
                        autoPlay: playing
                    )
                }
            }
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
    }
}
