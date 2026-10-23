import SwiftUI

/// The place finder on the whole screen, swiping and reshuffling by itself.
struct MoodCardShuffleDemo: View {
    var body: some View {
        MoodCardShuffle(autoPlay: true)
            .background(Color(hex: 0x2a2820).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    MoodCardShuffleDemo()
}
