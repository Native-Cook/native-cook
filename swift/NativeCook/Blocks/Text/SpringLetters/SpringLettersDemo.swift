import SwiftUI

/// The word on its pastel stage, being played with by itself until a finger joins in.
struct SpringLettersDemo: View {
    var body: some View {
        SpringLetters(autoPlay: true)
            .background(Color(hex: 0xefe8fb).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    SpringLettersDemo()
}
