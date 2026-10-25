import SwiftUI

/// The screening page on the whole display, its curtain opening by itself.
struct VelvetCurtainSheetDemo: View {
    var body: some View {
        VelvetCurtainSheet(autoPlay: true)
            .background(VelvetCurtainSheetArt.page.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    VelvetCurtainSheetDemo()
}
