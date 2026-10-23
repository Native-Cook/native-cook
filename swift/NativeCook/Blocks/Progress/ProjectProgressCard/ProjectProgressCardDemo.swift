import SwiftUI

struct ProjectProgressCardDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xf5f7f8), height: 330) { ProjectProgressCard(autoPlay: true) }
    }
}

#Preview {
    ProjectProgressCardDemo()
}
