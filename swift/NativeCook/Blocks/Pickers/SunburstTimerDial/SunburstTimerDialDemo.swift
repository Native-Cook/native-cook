import SwiftUI

struct SunburstTimerDialDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xF5F4F1), height: 380) { SunburstTimerDial(autoPlay: true) }
    }
}

#Preview {
    SunburstTimerDialDemo()
}
