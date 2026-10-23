import SwiftUI

/// The wall alone on a black screen, opening its gap and a tile by itself.
struct BreathingGapGridDemo: View {
    var body: some View {
        BreathingGapGrid(autoPlay: true)
            .background(Color(hex: 0x000000).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    BreathingGapGridDemo()
}
