import SwiftUI

struct BudgetScrubScaleDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xe6e6ec), height: 520) { BudgetScrubScale(autoPlay: true) }
    }
}

#Preview {
    BudgetScrubScaleDemo()
}
