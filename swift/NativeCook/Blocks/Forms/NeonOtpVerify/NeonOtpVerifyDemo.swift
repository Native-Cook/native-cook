import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct NeonOtpVerifyDemo: View {
    var body: some View {
        NeonOtpVerify(autoPlay: true)
    }
}

#Preview {
    NeonOtpVerifyDemo()
}
