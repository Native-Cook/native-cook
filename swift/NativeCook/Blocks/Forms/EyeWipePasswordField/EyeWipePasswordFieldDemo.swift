import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct EyeWipePasswordFieldDemo: View {
    var body: some View {
        EyeWipePasswordField(autoPlay: true)
    }
}

#Preview {
    EyeWipePasswordFieldDemo()
}
