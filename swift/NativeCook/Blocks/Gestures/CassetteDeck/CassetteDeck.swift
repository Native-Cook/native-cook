import SwiftUI

/// A full-screen mechanical cassette deck ported from the approved React Native block.
struct CassetteDeck: View {
    /// Seconds in one autoplay loop (`LOOP_MS / 1000` in the React Native reference).
    static let loop: Double = 10

    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        CassetteDeckSurfaceView(autoPlay: autoPlay && !reduceMotion, reduceMotion: reduceMotion)
            .background(Color(uiColor: CassetteDeckTheme.plate))
            .ignoresSafeArea()
            .preferredColorScheme(.light)
    }
}

private struct CassetteDeckSurfaceView: UIViewRepresentable {
    let autoPlay: Bool
    let reduceMotion: Bool

    func makeUIView(context: Context) -> CassetteDeckSurface {
        CassetteDeckSurface(autoPlay: autoPlay, reduceMotion: reduceMotion)
    }

    func updateUIView(_ view: CassetteDeckSurface, context: Context) {
        view.configure(autoPlay: autoPlay, reduceMotion: reduceMotion)
    }
}

#Preview {
    CassetteDeck(autoPlay: true)
}
