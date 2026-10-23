import SwiftUI

struct LikeBurstButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xfeeeef)) { LikeBurstButton(autoPlay: true) }
    }
}

#Preview {
    LikeBurstButtonDemo()
}
