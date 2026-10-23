import SwiftUI

struct StampDiaryCalendarDemo: View {
    var body: some View {
        StampDiaryCalendar(autoPlay: autoPlay)
    }

    private var autoPlay: Bool {
        #if DEBUG
        return !ProcessInfo.processInfo.arguments.contains("-stamp-manual")
        #else
        return true
        #endif
    }
}

#Preview {
    StampDiaryCalendarDemo()
}
