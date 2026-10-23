import SwiftUI

/// The block full screen on white, playing by itself, as the RN demo shows it.
struct DepthFieldGalleryDemo: View {
    var body: some View {
        DepthFieldGallery(autoPlay: true)
            .background(Color(hex: 0xffffff).ignoresSafeArea())
            .statusBarHidden()
            // The gallery this block is shown in asks for the dark scheme at its root, and an outer
            // preference wins over this one, so the block also sets the light scheme on its own views.
            .environment(\.colorScheme, .light)
            .preferredColorScheme(.light)
    }
}

#Preview {
    DepthFieldGalleryDemo()
}
