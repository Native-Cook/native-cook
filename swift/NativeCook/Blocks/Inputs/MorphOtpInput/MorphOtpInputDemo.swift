import SwiftUI

/// The code input on the whole screen, typing a wrong code and then the right one by itself.
struct MorphOtpInputDemo: View {
    var body: some View {
        MorphOtpInput(autoPlay: true)
            .background(Color(hex: 0x0c0d10).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    MorphOtpInputDemo()
}
