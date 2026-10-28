import SwiftUI

/// The Larder save screen on the whole screen, pouring picks into its folder by itself.
struct PrismBeamFolderDemo: View {
    var body: some View {
        PrismBeamFolder(autoPlay: true)
    }
}

#Preview {
    PrismBeamFolderDemo()
}
