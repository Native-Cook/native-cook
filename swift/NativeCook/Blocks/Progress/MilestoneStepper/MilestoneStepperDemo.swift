import SwiftUI

struct MilestoneStepperDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xe7e6e9), height: 340) { MilestoneStepper(autoPlay: true) }
    }
}

#Preview {
    MilestoneStepperDemo()
}
