import SwiftUI

struct GenerateButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x111111), height: 290) { GenerateButton(active: true) }
    }
}

#Preview {
    GenerateButtonDemo()
}
