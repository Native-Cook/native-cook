import SwiftUI

struct MissionGlowProgressDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x08060a), height: 300) { MissionGlowProgress(autoPlay: true) }
    }
}

#Preview {
    MissionGlowProgressDemo()
}
