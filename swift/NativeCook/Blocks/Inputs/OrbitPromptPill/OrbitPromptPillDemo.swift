import SwiftUI

struct OrbitPromptPillDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xf4f4f4), height: 380) { OrbitPromptPill(autoPlay: true) }
    }
}

#Preview {
    OrbitPromptPillDemo()
}
