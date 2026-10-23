import SwiftUI

/// The demo's finger: a soft grey disc that presses, moves and fades.
struct DragMultiSelectGhost: View {
    let engine: DragMultiSelectEngine

    var body: some View {
        let state = engine.state
        Circle()
            .fill(DragMultiSelectColor.finger)
            .overlay { Circle().strokeBorder(DragMultiSelectColor.fingerEdge, lineWidth: 1) }
            .frame(width: DragMultiSelectLayout.finger, height: DragMultiSelectLayout.finger)
            .scaleEffect(1 + DragMultiSelectTokens.Component.ghostPressScale * engine.ghostPress.value)
            .opacity(engine.ghostO.value)
            .offset(x: state.ghostX - DragMultiSelectLayout.finger / 2, y: state.ghostY - DragMultiSelectLayout.finger / 2)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}
