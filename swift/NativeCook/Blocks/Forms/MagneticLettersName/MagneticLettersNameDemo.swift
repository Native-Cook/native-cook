import SwiftUI

/// The fridge door fills the screen, spelling a name by itself.
struct MagneticLettersNameDemo: View {
    var body: some View {
        MagneticLettersName(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    MagneticLettersNameDemo()
}
