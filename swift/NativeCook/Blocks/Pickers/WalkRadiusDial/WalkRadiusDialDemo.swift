import SwiftUI

struct WalkRadiusDialDemo: View {
    var body: some View {
        ComponentStage(background: .white, height: 540) { WalkRadiusDial(autoPlay: true) }
    }
}

#Preview {
    WalkRadiusDialDemo()
}
