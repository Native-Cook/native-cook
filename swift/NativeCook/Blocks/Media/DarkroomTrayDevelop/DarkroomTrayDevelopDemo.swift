import SwiftUI

/// The print step alone on the whole screen, rocking, fixing and changing sheets by itself.
struct DarkroomTrayDevelopDemo: View {
    var body: some View {
        DarkroomTrayDevelop(autoPlay: true)
            .background(DarkroomTrayDevelopPalette.background.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    DarkroomTrayDevelopDemo()
}
