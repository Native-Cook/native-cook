import SwiftUI

/// The marquee on the whole screen, drifting and throwing itself.
struct VelocityMarqueeDemo: View {
    var body: some View {
        VelocityMarquee(autoPlay: true)
            .background(Color(hex: 0xf2efe8).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    VelocityMarqueeDemo()
}
