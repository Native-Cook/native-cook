import SwiftUI

struct RotatingSuggestionsInputDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xe4e4e9), height: 440) { RotatingSuggestionsInput(autoPlay: true) }
            // The composer stays where it is when the keyboard comes up.
            .ignoresSafeArea(.keyboard)
    }
}

#Preview {
    RotatingSuggestionsInputDemo()
}
