import SwiftUI

struct TransitRouteProgressDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xe7e7e7), height: 250) { TransitRouteProgress(autoPlay: true) }
    }
}

#Preview {
    TransitRouteProgressDemo()
}
