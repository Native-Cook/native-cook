import SwiftUI

/// The tray on the whole screen, dropping, combing and printing by itself.
struct MarblingCombDemo: View {
    var body: some View {
        MarblingComb(autoPlay: true)
            .background(Color(hex: 0x16243c).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    MarblingCombDemo()
}
