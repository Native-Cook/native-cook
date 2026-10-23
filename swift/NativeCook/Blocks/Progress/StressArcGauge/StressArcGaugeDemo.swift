import SwiftUI

struct StressArcGaugeDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xffffff), height: 290) { StressArcGauge(autoPlay: true) }
    }
}

#Preview {
    StressArcGaugeDemo()
}
