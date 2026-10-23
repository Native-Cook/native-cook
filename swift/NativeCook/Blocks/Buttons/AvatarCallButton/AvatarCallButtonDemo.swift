import SwiftUI

struct AvatarCallButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xeeeeee)) { AvatarCallButton(autoPlay: true) }
    }
}

#Preview {
    AvatarCallButtonDemo()
}
