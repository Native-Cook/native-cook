import SwiftUI

/// The memory wall on the whole screen, panning and bulging by itself.
struct FisheyeMemoryWallDemo: View {
    var body: some View {
        FisheyeMemoryWall(autoPlay: true)
    }
}

#Preview {
    FisheyeMemoryWallDemo()
}
