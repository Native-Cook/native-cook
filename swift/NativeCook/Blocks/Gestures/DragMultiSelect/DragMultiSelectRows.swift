import SwiftUI

/// Every mounted row, each at its own place in the list. The scroll view moves them; this only draws.
struct DragMultiSelectRows: View {
    let engine: DragMultiSelectEngine

    var body: some View {
        let state = engine.state
        ZStack(alignment: .topLeading) {
            ForEach(state.mounted) { row in
                DragMultiSelectRowView(row: row, engine: engine)
            }
        }
        .frame(width: state.width, height: engine.maxContentHeight, alignment: .topLeading)
    }
}
