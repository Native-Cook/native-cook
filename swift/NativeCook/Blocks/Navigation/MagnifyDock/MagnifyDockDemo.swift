import SwiftUI

/// The dock on its wallpaper, swept, launched and rearranged by a virtual finger.
struct MagnifyDockDemo: View {
    var body: some View {
        MagnifyDock(autoPlay: true)
            .background(Color(hex: 0x1d3150).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    MagnifyDockDemo()
}
