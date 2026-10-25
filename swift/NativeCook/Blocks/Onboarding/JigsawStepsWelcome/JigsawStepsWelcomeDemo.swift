import SwiftUI

/// The board and the pile fill the screen, the status bar hidden like the React Native demo.
struct JigsawStepsWelcomeDemo: View {
    var body: some View {
        JigsawStepsWelcome(autoPlay: true)
            .background(JigsawStepsWelcomePalette.bg.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    JigsawStepsWelcomeDemo()
}
