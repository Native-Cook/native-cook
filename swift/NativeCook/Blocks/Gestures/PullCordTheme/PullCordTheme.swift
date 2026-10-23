import SwiftUI

struct PullCordTheme: View {
    /// React Native LOOP_MS / 1000.
    static let loop: Double = 12

    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @Environment(\.scenePhase) private var scenePhase
    @State private var controller: PullCordThemeController

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _controller = State(initialValue: PullCordThemeController(autoPlay: autoPlay))
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(
                width: proxy.size.width + insets.leading + insets.trailing,
                height: proxy.size.height + insets.top + insets.bottom
            )
            stage(size: size)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Color.black.ignoresSafeArea())
        .statusBarHidden()
        .onChange(of: reduceMotion) { _, reduced in
            configure(reduced: reduced)
        }
        .onChange(of: autoPlay) { _, _ in
            configure(reduced: reduceMotion)
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { controller.start() }
            else { controller.stop() }
        }
        .onDisappear { controller.stop() }
    }

    private func stage(size: CGSize) -> some View {
        ZStack(alignment: .topLeading) {
            Canvas { context, canvasSize in
                PullCordThemeArt.draw(
                    context: &context,
                    size: canvasSize,
                    snapshot: controller.snapshot,
                    masks: controller.pageMasks
                )
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(PullCordThemePageMasks.accessibilityText)

            PullCordThemeTouchView(controller: controller)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .onAppear {
            controller.configure(
                size: size,
                autoPlay: autoPlay,
                reducedMotion: reduceMotion,
                displayScale: displayScale
            )
            controller.start()
        }
        .onChange(of: size) { _, newSize in
            controller.configure(
                size: newSize,
                autoPlay: autoPlay,
                reducedMotion: reduceMotion,
                displayScale: displayScale
            )
        }
    }

    private func configure(reduced: Bool) {
        let size = controller.snapshot.geometry
        controller.configure(
            size: CGSize(width: size.width, height: size.height),
            autoPlay: autoPlay,
            reducedMotion: reduced,
            displayScale: displayScale
        )
    }
}
