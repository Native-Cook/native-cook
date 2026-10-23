import SwiftUI

struct GrassTimerCarouselDemo: View {
    var body: some View {
        GrassTimerCarousel(autoPlay: autoPlay)
    }

    private var autoPlay: Bool {
        #if DEBUG
        return !ProcessInfo.processInfo.arguments.contains("-grass-manual")
        #else
        return true
        #endif
    }
}

#Preview {
    GrassTimerCarouselDemo()
}
