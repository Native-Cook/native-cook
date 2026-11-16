import SwiftUI

struct ExtrusionRecapStoryDemo: View {
    static let loop = ExtrusionRecapStory.loop
    var body: some View {
        ExtrusionRecapStory(autoPlay: true)
    }
}

#Preview {
    ExtrusionRecapStoryDemo()
}
