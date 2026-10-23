import SwiftUI

struct SleepRangeScaleDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xcfcdd8), height: 460) { SleepRangeScale(autoPlay: true) }
    }
}

#Preview {
    SleepRangeScaleDemo()
}
