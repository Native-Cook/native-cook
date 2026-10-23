import SwiftUI

/// The roll alone on its dark light table, winding, printing and dropping frames back by itself.
struct FilmStripReelDemo: View {
    var body: some View {
        FilmStripReel(autoPlay: true)
            .background(Color(hex: 0x0b0a09).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    FilmStripReelDemo()
}
