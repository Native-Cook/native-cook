import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct CameraCarouselFanDemo: View {
    var body: some View {
        CameraCarouselFan(autoPlay: true)
    }
}

#Preview {
    CameraCarouselFanDemo()
}
