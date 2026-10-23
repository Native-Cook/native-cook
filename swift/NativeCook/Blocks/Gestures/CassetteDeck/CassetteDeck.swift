import SwiftUI

/// A skeuomorphic tape deck: keys that click and latch, reels driven by the tape, and cassettes you
/// slide or turn by hand. The simulation and the clock live in `CassetteDeckModel`; this view is the artboard.
struct CassetteDeck: View {
    /// Seconds in one autoplay loop (`CassetteDeckMotion.loopMS` / 1000); it starts and ends at rest on the first cassette.
    static let loop: Double = 9

    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model: CassetteDeckModel

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _model = State(initialValue: CassetteDeckModel(autoPlay: autoPlay))
    }

    var body: some View {
        GeometryReader { proxy in
            let metrics = CassetteDeckMetrics(size: proxy.size)
            ZStack(alignment: .topLeading) {
                CassetteDeckTheme.Palette.bg
                CassetteDeckStage(model: model, metrics: metrics)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
                CassetteDeckTouchView(model: model)
                    .frame(width: proxy.size.width, height: proxy.size.height)
            }
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
        }
        .ignoresSafeArea()
        .preferredColorScheme(.dark)
        .onChange(of: reduceMotion, initial: true) { _, value in
            model.setReduceMotion(value)
        }
        .onAppear { model.start() }
        .onDisappear { model.stop() }
    }
}

#Preview {
    CassetteDeck(autoPlay: true)
}
