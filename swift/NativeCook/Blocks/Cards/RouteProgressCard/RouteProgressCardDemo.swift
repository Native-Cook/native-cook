import SwiftUI

struct RouteProgressCardDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xe8e8ea), height: 400) { RouteProgressCard(autoPlay: true) }
    }
}

#Preview {
    RouteProgressCardDemo()
}
