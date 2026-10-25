import SwiftUI

/// The wall and the projector fill the screen, the status bar hidden like the React Native demo.
struct SlideProjectorGalleryDemo: View {
    var body: some View {
        SlideProjectorGallery(autoPlay: true)
            .background(SlideProjectorGalleryPalette.bg.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    SlideProjectorGalleryDemo()
}
