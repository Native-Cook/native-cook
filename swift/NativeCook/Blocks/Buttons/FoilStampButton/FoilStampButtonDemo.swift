import SwiftUI

/// The checkout on the whole screen, the stamp striking by itself.
struct FoilStampButtonDemo: View {
    var body: some View {
        FoilStampButton(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    FoilStampButtonDemo()
}
