import SwiftUI

/// The members screen on the whole screen, its card warming and cooling by itself.
struct ThermalRevealCardDemo: View {
    var body: some View {
        ThermalRevealCard(autoPlay: true)
            .background(Color(hex: 0x0e1219).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    ThermalRevealCardDemo()
}
