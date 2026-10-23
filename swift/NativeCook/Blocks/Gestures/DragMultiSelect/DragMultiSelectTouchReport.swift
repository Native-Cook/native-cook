import SwiftUI

/// Tells the engine when a finger is on the bars, so the demo stops for it as it does for the list.
struct DragMultiSelectTouchReport: ViewModifier {
    let engine: DragMultiSelectEngine
    @GestureState private var down = false

    func body(content: Content) -> some View {
        content
            .simultaneousGesture(
                DragGesture(minimumDistance: 0).updating($down) { _, state, _ in state = true }
            )
            .onChange(of: down) { _, isDown in
                if isDown {
                    engine.userTouchBegan()
                } else {
                    engine.userTouchEnded()
                }
            }
    }
}
