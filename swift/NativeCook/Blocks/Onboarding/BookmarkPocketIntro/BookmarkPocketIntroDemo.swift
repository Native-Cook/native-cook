import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct BookmarkPocketIntroDemo: View {
    var body: some View {
        BookmarkPocketIntro(autoPlay: true)
    }
}

#Preview {
    BookmarkPocketIntroDemo()
}
