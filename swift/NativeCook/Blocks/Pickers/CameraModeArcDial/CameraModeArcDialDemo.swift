import SwiftUI

struct CameraModeArcDialDemo: View {
    var body: some View {
        ComponentStage(background: .black, height: 360) { CameraModeArcDial(autoPlay: true) }
    }
}

#Preview {
    CameraModeArcDialDemo()
}
