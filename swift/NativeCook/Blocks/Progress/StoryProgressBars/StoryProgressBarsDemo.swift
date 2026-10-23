import SwiftUI

struct StoryProgressBarsDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x101010), height: 400) { StoryProgressBars(autoPlay: true) }
    }
}

#Preview {
    StoryProgressBarsDemo()
}
