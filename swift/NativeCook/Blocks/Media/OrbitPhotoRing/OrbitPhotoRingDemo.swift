import SwiftUI

/// The camera and its ring of prints on a dark screen, playing its loop by itself.
struct OrbitPhotoRingDemo: View {
    var body: some View {
        OrbitPhotoRing(autoPlay: true)
            .background(Color(orbitHex: 0x050506).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    OrbitPhotoRingDemo()
}
