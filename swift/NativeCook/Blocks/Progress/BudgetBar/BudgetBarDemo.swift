import SwiftUI

struct BudgetBarDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xfdfdfd), height: 360) { BudgetBar(autoPlay: true) }
    }
}

#Preview {
    BudgetBarDemo()
}
