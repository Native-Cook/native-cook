import SwiftUI

/// The island and its toasts on the whole screen, playing through by themselves.
struct IslandToastStackDemo: View {
    var body: some View {
        IslandToastStack(autoPlay: true)
            .background(Color(hex: 0x07080e).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    IslandToastStackDemo()
}
