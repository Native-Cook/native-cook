import SwiftUI

/// A to-do list: press and drag down the list to select many rows at once.
struct DragMultiSelect: View {
    /// Plays a 13 s loop: a range dragged and dragged back, one more row tapped, a drag into the bottom
    /// edge, Done, then two rows picked, deleted and put back.
    var autoPlay = false
    /// Seconds: `LOOP_MS` of the React Native block, 13 000 ms (the script's `loopMS`).
    // DragMultiSelectTokens.Demo.LOOP_MS / 1000; a literal so the catalog check can read it
    static let loop: Double = 13

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = DragMultiSelectEngine()

    var body: some View {
        // The block draws under the system bars and keeps its rows clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            screen(size: size, insets: insets)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(DragMultiSelectColor.background.ignoresSafeArea())
        .preferredColorScheme(.light)
    }

    private func screen(size: CGSize, insets: EdgeInsets) -> some View {
        ZStack(alignment: .topLeading) {
            DragMultiSelectColor.background
            DragMultiSelectList(
                engine: engine,
                size: size,
                topInset: insets.top,
                bottomInset: insets.bottom,
                autoPlay: autoPlay,
                reduceMotion: reduceMotion
            )
            DragMultiSelectNavBar(engine: engine, topInset: insets.top)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            DragMultiSelectToolbar(engine: engine, width: size.width, bottomInset: insets.bottom)
            if autoPlay && !DragMultiSelectDemoScript.hidesFinger {
                DragMultiSelectGhost(engine: engine)
            }
        }
        .clipped()
    }
}
