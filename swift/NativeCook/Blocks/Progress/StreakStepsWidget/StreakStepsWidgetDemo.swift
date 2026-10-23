import SwiftUI

struct StreakStepsWidgetDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x141414), height: 390) { StreakStepsWidget(autoPlay: true) }
    }
}

#Preview {
    StreakStepsWidgetDemo()
}
