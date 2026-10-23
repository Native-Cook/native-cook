import SwiftUI

struct MoonOrbitTimeScrubberDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x05061A), height: 680) { MoonOrbitTimeScrubber(autoPlay: true) }
    }
}

#Preview {
    MoonOrbitTimeScrubberDemo()
}
