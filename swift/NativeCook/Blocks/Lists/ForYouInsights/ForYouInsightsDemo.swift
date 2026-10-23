import SwiftUI

struct ForYouInsightsDemo: View {
    var body: some View {
        ForYouInsights(autoPlay: autoPlay)
    }

    private var autoPlay: Bool {
        #if DEBUG
        return !ProcessInfo.processInfo.arguments.contains("-insights-manual")
        #else
        return true
        #endif
    }
}

#Preview {
    ForYouInsightsDemo()
}
