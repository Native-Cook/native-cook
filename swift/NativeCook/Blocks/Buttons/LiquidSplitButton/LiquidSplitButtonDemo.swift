import SwiftUI

struct LiquidSplitButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xacb0b6)) { LiquidSplitButton(autoPlay: true) }
    }
}

#Preview {
    LiquidSplitButtonDemo()
}
