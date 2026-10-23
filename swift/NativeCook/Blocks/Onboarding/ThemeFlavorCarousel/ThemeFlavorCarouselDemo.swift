import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct ThemeFlavorCarouselDemo: View {
    var body: some View {
        ThemeFlavorCarousel(autoPlay: true)
    }
}

#Preview {
    ThemeFlavorCarouselDemo()
}
