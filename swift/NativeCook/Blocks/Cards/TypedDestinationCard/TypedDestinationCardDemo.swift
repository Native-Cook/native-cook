import SwiftUI

struct TypedDestinationCardDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xf5f3ee), height: 520) { TypedDestinationCard(autoPlay: true) }
    }
}

#Preview {
    TypedDestinationCardDemo()
}
