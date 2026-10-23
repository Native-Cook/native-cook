import SwiftUI

struct IridescentHomeButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x050506)) { IridescentHomeButton(autoPlay: true) }
    }
}

#Preview {
    IridescentHomeButtonDemo()
}
