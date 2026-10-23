import SwiftUI

struct NightSleepScoreDemo: View {
    private var shouldPlay: Bool {
        #if DEBUG
        return !ProcessInfo.processInfo.arguments.contains("-sleep-manual")
        #else
        return true
        #endif
    }

    var body: some View {
        NightSleepScore(autoPlay: shouldPlay)
    }
}

#Preview { NightSleepScoreDemo() }
