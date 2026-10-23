import SwiftUI

struct PowerRateSegmentsDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x020403), height: 384) { PowerRateSegments(autoPlay: true) }
    }
}

#Preview {
    PowerRateSegmentsDemo()
}
