import SwiftUI

/// The kaleidoscope alone, turning, shaking and changing its mirrors by itself.
struct KaleidoscopeTwistDemo: View {
    var body: some View {
        KaleidoscopeTwist(autoPlay: true)
            .background(Color(hex: 0x0b090d).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    KaleidoscopeTwistDemo()
}
