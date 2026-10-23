import SwiftUI

struct LetterWaveButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xfcfcfc)) { LetterWaveButton(autoPlay: true) }
    }
}

#Preview {
    LetterWaveButtonDemo()
}
