import SwiftUI

/// The banner alone over the sea at dusk, flying, pulled and stilled by itself.
struct ClothFlagMeshDemo: View {
    var body: some View {
        ClothFlagMesh(autoPlay: true)
            .background(Color(hex: ClothFlagMeshPalette.night).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    ClothFlagMeshDemo()
}
