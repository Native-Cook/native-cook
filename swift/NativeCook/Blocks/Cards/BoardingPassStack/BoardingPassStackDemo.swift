import SwiftUI

struct BoardingPassStackDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x05070c), height: 620) { BoardingPassStack(autoPlay: true) }
    }
}

#Preview {
    BoardingPassStackDemo()
}
