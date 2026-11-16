import SwiftUI

struct SkyTimeScrubDemo: View {
    static let loop = SkyTimeScrub.loop
    var body: some View {
        SkyTimeScrub(autoPlay: true)
    }
}

#Preview {
    SkyTimeScrubDemo()
}
