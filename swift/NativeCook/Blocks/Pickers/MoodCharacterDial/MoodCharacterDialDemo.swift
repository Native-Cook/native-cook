import SwiftUI

struct MoodCharacterDialDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x141b1c), height: 660) { MoodCharacterDial(autoPlay: true) }
    }
}

#Preview {
    MoodCharacterDialDemo()
}
