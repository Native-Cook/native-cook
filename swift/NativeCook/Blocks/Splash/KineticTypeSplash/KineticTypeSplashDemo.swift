import SwiftUI

/// The type on the whole screen, rising, rolling, opening onto the photo and starting over by itself.
struct KineticTypeSplashDemo: View {
    var body: some View {
        KineticTypeSplash(autoPlay: true)
            .background(Color(hex: 0x0a0a0b).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    KineticTypeSplashDemo()
}
