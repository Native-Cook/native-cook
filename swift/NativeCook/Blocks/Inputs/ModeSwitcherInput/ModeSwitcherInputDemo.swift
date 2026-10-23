import SwiftUI

struct ModeSwitcherInputDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x0b0c12), height: 420) { ModeSwitcherInput(autoPlay: true) }
    }
}

#Preview {
    ModeSwitcherInputDemo()
}
