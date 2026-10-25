import SwiftUI

/// The autumn headline on the whole screen, blowing apart and settling by itself.
struct WindScatterLettersDemo: View {
    var body: some View {
        WindScatterLetters(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    WindScatterLettersDemo()
}
