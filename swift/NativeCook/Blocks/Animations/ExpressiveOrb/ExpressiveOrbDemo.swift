import SwiftUI

/// The original app's root, `ContentView`: a dark scroll view, with no header and no status bar,
/// that holds the Orb and its expression picker.
struct ExpressiveOrbDemo: View {
    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            GrokOrbExpressionPicker()
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 36)
        }
        .background(GrokOrbPalette.screenBackground.ignoresSafeArea())
        .statusBarHidden(true)
        .preferredColorScheme(.dark)
    }
}

#Preview {
    ExpressiveOrbDemo()
}
