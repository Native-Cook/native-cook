import SwiftUI

struct BranchingPriceWheelDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xeef3f7), height: 580) { BranchingPriceWheel(autoPlay: true) }
    }
}

#Preview {
    BranchingPriceWheelDemo()
}
