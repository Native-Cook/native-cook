import SwiftUI

struct ModelPickerPromptDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x0e0d0c), height: 520) { ModelPickerPrompt(autoPlay: true) }
            // The prompt box stays where it is when the keyboard comes up.
            .ignoresSafeArea(.keyboard)
    }
}

#Preview {
    ModelPickerPromptDemo()
}
