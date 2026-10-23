import SwiftUI

struct PricingSegmentedButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xfdfdfd)) { PricingSegmentedButton(autoPlay: true) }
    }
}

#Preview {
    PricingSegmentedButtonDemo()
}
