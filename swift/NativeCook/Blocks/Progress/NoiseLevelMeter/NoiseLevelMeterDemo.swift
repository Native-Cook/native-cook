import SwiftUI

struct NoiseLevelMeterDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xced2da), height: 270) {
            HStack(spacing: 12) {
                NoiseLevelMeter(autoPlay: true, level: 40)
                NoiseLevelMeter(autoPlay: true, level: 60)
            }
        }
    }
}

#Preview {
    NoiseLevelMeterDemo()
}
