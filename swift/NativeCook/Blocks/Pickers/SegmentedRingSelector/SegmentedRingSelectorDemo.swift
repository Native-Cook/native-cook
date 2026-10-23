import SwiftUI

struct SegmentedRingSelectorDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x22343a), height: 380) { SegmentedRingSelector(autoPlay: true) }
    }
}

#Preview {
    SegmentedRingSelectorDemo()
}
