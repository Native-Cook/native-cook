import SwiftUI

/// One block on the whole screen, with nothing of the gallery over it, so a recording shows only
/// the block. Swipe in from the left edge to go back.
struct DemoScreen: View {
    let block: Block
    let onBack: () -> Void

    var body: some View {
        block.demo()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(GalleryColors.background.ignoresSafeArea())
            // Runs beside the block's own gestures and takes no touches from it: only a drag that
            // starts at the left edge and travels right, like the system's back swipe, goes back.
            .simultaneousGesture(
                DragGesture(minimumDistance: 24, coordinateSpace: .global).onEnded { drag in
                    let across = drag.translation.width
                    if drag.startLocation.x < 24, across > 80, abs(drag.translation.height) < across / 2 {
                        onBack()
                    }
                }
            )
            .accessibilityAction(.escape, onBack)
    }
}
