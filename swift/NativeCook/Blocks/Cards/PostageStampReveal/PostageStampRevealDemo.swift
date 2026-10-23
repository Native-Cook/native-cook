import SwiftUI

struct PostageStampRevealDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xc8d9f0), height: 520) { PostageStampReveal(autoPlay: true) }
    }
}

#Preview {
    PostageStampRevealDemo()
}
