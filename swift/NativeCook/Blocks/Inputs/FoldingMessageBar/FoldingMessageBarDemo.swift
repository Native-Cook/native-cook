import SwiftUI

struct FoldingMessageBarDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x00080a), height: 390) { FoldingMessageBar(autoPlay: true) }
    }
}

#Preview {
    FoldingMessageBarDemo()
}
