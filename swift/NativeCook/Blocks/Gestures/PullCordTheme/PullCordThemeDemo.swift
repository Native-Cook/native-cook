import SwiftUI

struct PullCordThemeDemo: View {
    var body: some View {
        PullCordTheme(autoPlay: true)
            .statusBarHidden()
            .persistentSystemOverlays(.hidden)
    }
}

#Preview {
    PullCordThemeDemo()
}
