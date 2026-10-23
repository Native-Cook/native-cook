import SwiftUI

struct SubmitConfettiButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xeff4fc), height: 280) { SubmitConfettiButton(autoPlay: true) }
    }
}

#Preview {
    SubmitConfettiButtonDemo()
}
