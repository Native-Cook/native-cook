import SwiftUI

/// The cradle on its desk, playing its 8 s loop, with the status bar hidden.
struct NewtonCradleSwingDemo: View {
    var body: some View {
        NewtonCradleSwing(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    NewtonCradleSwingDemo()
}
