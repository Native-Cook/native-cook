import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct ShieldPasswordStrengthDemo: View {
    var body: some View {
        ShieldPasswordStrength(autoPlay: true)
    }
}

#Preview {
    ShieldPasswordStrengthDemo()
}
