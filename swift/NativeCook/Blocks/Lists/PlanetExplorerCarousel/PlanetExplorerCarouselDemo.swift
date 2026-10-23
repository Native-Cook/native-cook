import SwiftUI

struct PlanetExplorerCarouselDemo: View {
    private var freezeAt: TimeInterval? {
#if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        guard let index = arguments.firstIndex(of: "-freezeAt"), arguments.indices.contains(index + 1) else { return nil }
        return TimeInterval(arguments[index + 1])
#else
        return nil
#endif
    }

    var body: some View { PlanetExplorerCarousel(autoPlay: freezeAt == nil, freezeAt: freezeAt) }
}

#Preview { PlanetExplorerCarouselDemo() }
