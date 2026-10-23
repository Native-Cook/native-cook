import SwiftUI

struct VoiceNoteComposerDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x161d27), height: 340) { VoiceNoteComposer(autoPlay: true) }
    }
}

#Preview {
    VoiceNoteComposerDemo()
}
