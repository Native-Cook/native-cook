import SwiftUI

/// The pattern lock alone on its dark screen, drawing a wrong pattern and then the right one.
struct PatternLockInputDemo: View {
    var body: some View {
        PatternLockInput(autoPlay: true)
            .background(Color(hex: 0x05060a).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    PatternLockInputDemo()
}
