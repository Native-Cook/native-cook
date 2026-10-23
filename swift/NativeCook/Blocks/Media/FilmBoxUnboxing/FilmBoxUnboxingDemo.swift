import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct FilmBoxUnboxingDemo: View {
    var body: some View {
        FilmBoxUnboxing(autoPlay: true)
    }
}

#Preview {
    FilmBoxUnboxingDemo()
}
