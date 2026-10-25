import SwiftUI

/// The lock screen on the whole screen, entering a wrong and a right code by itself.
struct SandPinCodeDemo: View {
    var body: some View {
        SandPinCode(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    SandPinCodeDemo()
}
