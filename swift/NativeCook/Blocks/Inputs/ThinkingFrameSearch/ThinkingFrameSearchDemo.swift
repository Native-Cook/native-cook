import SwiftUI

struct ThinkingFrameSearchDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xe8e8e8), height: 340) { ThinkingFrameSearch(autoPlay: true) }
    }
}

#Preview {
    ThinkingFrameSearchDemo()
}
