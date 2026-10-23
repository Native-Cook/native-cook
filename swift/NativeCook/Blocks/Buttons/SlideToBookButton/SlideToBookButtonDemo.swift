import SwiftUI

struct SlideToBookButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xfafafa)) { SlideToBookButton(autoPlay: true) }
    }
}

#Preview {
    SlideToBookButtonDemo()
}
