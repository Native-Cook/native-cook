import SwiftUI

/// The block fills the screen, so it is shown without a stage.
struct BannerGrowCardDemo: View {
    var body: some View {
        BannerGrowCard(autoPlay: true)
    }
}

#Preview {
    BannerGrowCardDemo()
}
