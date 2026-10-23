import SwiftUI

struct TiltButtonDemo: View {
    var body: some View {
        ComponentStage(background: .black) { TiltButton(autoPlay: true) }
    }
}

#Preview {
    TiltButtonDemo()
}
