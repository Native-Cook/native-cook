import SwiftUI

struct ExpandingTripCardDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xe9eaec), height: 680) { ExpandingTripCard(autoPlay: true) }
    }
}

#Preview {
    ExpandingTripCardDemo()
}
