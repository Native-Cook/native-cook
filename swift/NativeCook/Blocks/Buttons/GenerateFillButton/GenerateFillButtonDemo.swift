import SwiftUI

struct GenerateFillButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xffffff)) { GenerateFillButton(autoPlay: true) }
    }
}

#Preview {
    GenerateFillButtonDemo()
}
