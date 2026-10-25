import SwiftUI

/// The journal's lock screen on the whole display, unzipping itself.
struct ZipperUnlockDemo: View {
    var body: some View {
        ZipperUnlock(autoPlay: true)
            .background(Color(hex: 0x17243F).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    ZipperUnlockDemo()
}
