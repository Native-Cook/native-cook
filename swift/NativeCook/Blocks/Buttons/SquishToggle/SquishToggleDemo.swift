import SwiftUI

struct SquishToggleDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xeeeff4)) { SquishToggle(autoPlay: true) }
    }
}

#Preview {
    SquishToggleDemo()
}
