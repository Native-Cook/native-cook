import SwiftUI

struct ActivityRingsDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x4b4b4b), height: 280) { ActivityRings(autoPlay: true) }
    }
}

#Preview {
    ActivityRingsDemo()
}
