import SwiftUI

struct SkyScreenTimeDemo: View {
    var body: some View {
        #if DEBUG
        SkyScreenTime(autoPlay: !ProcessInfo.processInfo.arguments.contains("-sky-manual"))
        #else
        SkyScreenTime(autoPlay: true)
        #endif
    }
}

#Preview { SkyScreenTimeDemo() }
