import SwiftUI

struct PlaybackDialDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x040404), height: 340) { PlaybackDial(autoPlay: true) }
    }
}

#Preview {
    PlaybackDialDemo()
}
