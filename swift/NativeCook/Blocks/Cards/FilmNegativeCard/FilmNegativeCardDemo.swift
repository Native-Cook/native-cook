import SwiftUI

/// The strip and the light table fill the screen, the status bar hidden like the React Native demo.
struct FilmNegativeCardDemo: View {
    var body: some View {
        FilmNegativeCard(autoPlay: true)
            .background(FilmNegativeCardPalette.bg.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    FilmNegativeCardDemo()
}
