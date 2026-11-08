import SwiftUI

/// The Courtside feed on the whole screen, pulling and shooting by itself.
struct HoopShotRefreshDemo: View {
    var body: some View {
        HoopShotRefresh(autoPlay: true)
    }
}

#Preview {
    HoopShotRefreshDemo()
}
