import SwiftUI

struct OrderTruckButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xe1ebf7)) { OrderTruckButton(autoPlay: true) }
    }
}

#Preview {
    OrderTruckButtonDemo()
}
