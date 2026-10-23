import SwiftUI

struct DataUsageRingDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xf4f4f4), height: 440) { DataUsageRing(autoPlay: true) }
    }
}

#Preview {
    DataUsageRingDemo()
}
