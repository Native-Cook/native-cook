import SwiftUI

struct TrailRouteCardDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xf0f0f0), height: 480) { TrailRouteCard(autoPlay: true) }
    }
}

#Preview {
    TrailRouteCardDemo()
}
