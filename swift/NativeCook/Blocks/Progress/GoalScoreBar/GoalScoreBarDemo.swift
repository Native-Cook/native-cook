import SwiftUI

struct GoalScoreBarDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xe8e8e8), height: 310) { GoalScoreBar(autoPlay: true) }
    }
}

#Preview {
    GoalScoreBarDemo()
}
