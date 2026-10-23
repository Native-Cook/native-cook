import SwiftUI

struct MorphingMicPillDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xe6e4df), height: 480) { MorphingMicPill(autoPlay: true) }
    }
}

#Preview {
    MorphingMicPillDemo()
}
