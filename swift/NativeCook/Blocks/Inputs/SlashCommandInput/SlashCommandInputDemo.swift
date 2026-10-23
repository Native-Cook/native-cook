import SwiftUI

struct SlashCommandInputDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x141414), height: 440) { SlashCommandInput(autoPlay: true) }
    }
}

#Preview {
    SlashCommandInputDemo()
}
