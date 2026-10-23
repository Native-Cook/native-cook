import SwiftUI

/// The focus timer on its table in afternoon light, setting a session and running it out by itself.
struct SandHourglassDemo: View {
    var body: some View {
        SandHourglass(autoPlay: true)
            .background(Color(hex: SandHourglassPalette.wall).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    SandHourglassDemo()
}
