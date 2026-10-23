import SwiftUI

struct GlowBorderChatbarDemo: View {
    var body: some View {
        ComponentStage(background: .black, height: 300) { GlowBorderChatbar(autoPlay: true) }
    }
}

#Preview {
    GlowBorderChatbarDemo()
}
