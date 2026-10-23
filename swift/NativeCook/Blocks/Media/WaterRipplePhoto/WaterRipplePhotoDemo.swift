import SwiftUI

/// The photo on water on the whole screen, raining and swiping by itself, under a hidden status bar.
struct WaterRipplePhotoDemo: View {
    var body: some View {
        WaterRipplePhoto(autoPlay: true)
            .background(Color(hex: 0x0d0c11).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    WaterRipplePhotoDemo()
}
