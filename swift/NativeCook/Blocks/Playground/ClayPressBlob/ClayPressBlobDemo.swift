import SwiftUI

/// The clay alone on its studio sweep, being poked, pulled, thrown and squeezed by itself.
struct ClayPressBlobDemo: View {
    var body: some View {
        ClayPressBlob(autoPlay: true)
            .background(Color(hex: 0xF2EEE8).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    ClayPressBlobDemo()
}
