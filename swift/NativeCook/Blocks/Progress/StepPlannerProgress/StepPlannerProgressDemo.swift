import SwiftUI

struct StepPlannerProgressDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x0a0a0a), height: 446) { StepPlannerProgress(autoPlay: true) }
    }
}

#Preview {
    StepPlannerProgressDemo()
}
