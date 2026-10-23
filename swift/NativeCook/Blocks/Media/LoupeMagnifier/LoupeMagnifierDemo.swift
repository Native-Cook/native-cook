import SwiftUI

/// The photo and its loupe on the whole screen, the loupe wandering by itself.
struct LoupeMagnifierDemo: View {
    var body: some View {
        LoupeMagnifier(autoPlay: true)
            .background(Color(hex: 0x0b0c10).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    LoupeMagnifierDemo()
}
