import SwiftUI

struct DeliveryStepsProgressDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xfdfdfd), height: 280) { DeliveryStepsProgress(autoPlay: true) }
    }
}

#Preview {
    DeliveryStepsProgressDemo()
}
