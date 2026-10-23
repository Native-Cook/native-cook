import SwiftUI

struct NotchRadialMenuDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x120b24), height: 440) { NotchRadialMenu(autoPlay: true) }
    }
}

#Preview {
    NotchRadialMenuDemo()
}
