import SwiftUI

/// The telegraph puzzle screen alone, full screen, keying "HI" until touched.
struct MorseKeyInputDemo: View {
    var body: some View {
        MorseKeyInput(autoPlay: true)
            .background(Color(hex: 0x111D1A).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    MorseKeyInputDemo()
}
