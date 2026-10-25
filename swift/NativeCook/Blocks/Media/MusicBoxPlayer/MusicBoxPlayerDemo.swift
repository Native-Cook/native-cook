import SwiftUI

/// The sleep-sounds screen on its own, its music box cranking the song by itself.
struct MusicBoxPlayerDemo: View {
    var body: some View {
        MusicBoxPlayer(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    MusicBoxPlayerDemo()
}
