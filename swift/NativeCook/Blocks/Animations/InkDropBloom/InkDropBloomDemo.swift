import SwiftUI

/// The palette generator on the whole screen, its glass dropping and filtering ink by itself.
struct InkDropBloomDemo: View {
    var body: some View {
        InkDropBloom(autoPlay: true)
            .background(InkDropBloomPalette.room.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    InkDropBloomDemo()
}
