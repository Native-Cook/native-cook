import SwiftUI

/// The tray alone on the whole screen, tilting and shaking by itself.
struct GlassMarblesTrayDemo: View {
    var body: some View {
        GlassMarblesTray(autoPlay: true)
            .background(GlassMarblesTrayPainter.table[2].ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    GlassMarblesTrayDemo()
}
