import SwiftUI

struct HoldDragResize: View {
    static let loop = 10.0

    let autoPlay: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @StateObject private var interaction = HoldDragResizeInteraction()

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
    }

    var body: some View {
        GeometryReader { proxy in
            let screenInsets = HoldDragResizeScreenInsets.current
            let layout = HoldDragResizeLayout(
                width: proxy.size.width,
                height: proxy.size.height,
                top: max(proxy.safeAreaInsets.top, screenInsets.top),
                bottom: max(proxy.safeAreaInsets.bottom, screenInsets.bottom)
            )

            TimelineView(.animation(paused: !autoPlay && !interaction.needsFrames)) { timeline in
                let snapshot = interaction.snapshot(
                    at: timeline.date,
                    layout: layout,
                    autoPlay: autoPlay,
                    reduceMotion: reduceMotion
                )

                ZStack(alignment: .topLeading) {
                    HoldDragResizeScene(layout: layout, snapshot: snapshot)
                    HoldDragResizeTouchSurface(
                        interaction: interaction,
                        layout: layout,
                        autoPlay: autoPlay,
                        reduceMotion: reduceMotion
                    )
                    .accessibilityHidden(true)
                }
                .frame(width: layout.width, height: layout.height, alignment: .topLeading)
            }
            .onAppear {
                HoldDragResizeFonts.register()
                interaction.configure(layout: layout, autoPlay: autoPlay, reduceMotion: reduceMotion)
            }
            .onChange(of: proxy.size) { _, _ in
                interaction.configure(layout: layout, autoPlay: autoPlay, reduceMotion: reduceMotion)
            }
            .onChange(of: reduceMotion) { _, value in
                interaction.configure(layout: layout, autoPlay: autoPlay, reduceMotion: value)
            }
        }
        .ignoresSafeArea()
        .background(HoldDragResizePalette.page)
        .preferredColorScheme(.dark)
    }
}

enum HoldDragResizeScreenInsets {
    static var current: UIEdgeInsets {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?
            .safeAreaInsets ?? .zero
    }
}
