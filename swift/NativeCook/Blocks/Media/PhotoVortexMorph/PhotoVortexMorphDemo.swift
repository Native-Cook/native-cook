import SwiftUI

/// The photos alone on their paper, morphing through the five layouts by themselves.
struct PhotoVortexMorphDemo: View {
    var body: some View {
        PhotoVortexMorph(autoPlay: true)
            .background(Color(hex: 0xf3f2ee).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    PhotoVortexMorphDemo()
}
