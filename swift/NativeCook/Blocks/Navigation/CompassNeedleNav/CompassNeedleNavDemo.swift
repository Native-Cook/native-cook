import SwiftUI

/// The hiking app's compass navigation alone, full screen, swinging round its tabs until touched.
struct CompassNeedleNavDemo: View {
    var body: some View {
        CompassNeedleNav(autoPlay: true)
            .background(Color(hex: 0x0F1C18).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    CompassNeedleNavDemo()
}
