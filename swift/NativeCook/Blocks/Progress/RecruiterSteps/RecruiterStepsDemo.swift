import SwiftUI

struct RecruiterStepsDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x3f6fae), height: 240) { RecruiterSteps(autoPlay: true) }
    }
}

#Preview {
    RecruiterStepsDemo()
}
