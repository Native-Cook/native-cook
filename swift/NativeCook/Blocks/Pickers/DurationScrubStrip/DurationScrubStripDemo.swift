import SwiftUI

struct DurationScrubStripDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x161616), height: 200) { DurationScrubStrip(autoPlay: true) }
    }
}

#Preview {
    DurationScrubStripDemo()
}
