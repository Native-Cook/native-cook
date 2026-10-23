import SwiftUI

/// The sign alone on its wall, powering on, browning out, swinging and powering down by itself.
struct NeonSignFlickerDemo: View {
    var body: some View {
        NeonSignFlicker(autoPlay: true)
            .background(Color(hex: 0x0c0908).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    NeonSignFlickerDemo()
}
