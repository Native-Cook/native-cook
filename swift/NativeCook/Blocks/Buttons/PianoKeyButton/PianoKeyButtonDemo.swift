import SwiftUI

/// The practice screen alone, full screen, playing its loop until touched.
struct PianoKeyButtonDemo: View {
    var body: some View {
        PianoKeyButton(autoPlay: true)
            .background(Color(hex: 0x15100D).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    PianoKeyButtonDemo()
}
