import SwiftUI

/// The tree on the whole screen, growing, blooming and turning through the seasons by itself.
struct FractalTreeBloomDemo: View {
    var body: some View {
        FractalTreeBloom(autoPlay: true)
            .background(Color(hex: 0xf6e6df).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    FractalTreeBloomDemo()
}
