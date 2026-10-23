import SwiftUI

/// The dial on the whole screen, dialling a number by itself.
struct RotaryPhoneDialDemo: View {
    var body: some View {
        RotaryPhoneDial(autoPlay: true)
            .background(RotaryPhoneDialDrawing.body.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    RotaryPhoneDialDemo()
}
