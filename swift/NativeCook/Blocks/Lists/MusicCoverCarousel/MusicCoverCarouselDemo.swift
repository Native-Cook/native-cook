import SwiftUI

struct MusicCoverCarouselDemo: View {
    var body: some View {
        MusicCoverCarousel(autoPlay: freezeAt == nil, freezeAt: freezeAt)
    }

    private var freezeAt: Double? {
#if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        guard let flag = arguments.firstIndex(of: "-freezeAt"),
              arguments.indices.contains(flag + 1)
        else { return nil }
        return Double(arguments[flag + 1])
#else
        nil
#endif
    }
}

#Preview {
    MusicCoverCarouselDemo()
}
