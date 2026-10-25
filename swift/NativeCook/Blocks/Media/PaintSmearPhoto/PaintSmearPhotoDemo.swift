import SwiftUI

/// The Atelier editor on the whole screen, painting and cleaning its photo by itself.
struct PaintSmearPhotoDemo: View {
    var body: some View {
        PaintSmearPhoto(autoPlay: true)
            .background(Color(hex: 0x1a1613).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    PaintSmearPhotoDemo()
}
