import SwiftUI

/// The dish alone on the whole screen, its magnets moving by themselves.
struct FerrofluidBlobDemo: View {
    var body: some View {
        FerrofluidBlob(autoPlay: true)
            .background(FerrofluidBlobMetrics.background.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    FerrofluidBlobDemo()
}
