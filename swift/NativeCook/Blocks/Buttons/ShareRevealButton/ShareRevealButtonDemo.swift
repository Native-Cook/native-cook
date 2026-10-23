import SwiftUI

struct ShareRevealButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x080720)) { ShareRevealButton(autoPlay: true) }
    }
}

#Preview {
    ShareRevealButtonDemo()
}
