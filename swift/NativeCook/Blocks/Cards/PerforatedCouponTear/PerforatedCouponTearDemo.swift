import SwiftUI

/// The coupon screen alone, tearing, filing and stamping its stub by itself.
struct PerforatedCouponTearDemo: View {
    var body: some View {
        PerforatedCouponTear(autoPlay: true)
            .background(PerforatedCouponTearPalette.paper[1].ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    PerforatedCouponTearDemo()
}
