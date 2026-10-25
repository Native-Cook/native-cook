import SwiftUI

/// The campfire chat on the whole screen, its smoke message forming and blowing away by itself.
struct SmokeSignalToastDemo: View {
    var body: some View {
        SmokeSignalToast(autoPlay: true)
            .background(SmokeSignalToastPalette.base.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    SmokeSignalToastDemo()
}
