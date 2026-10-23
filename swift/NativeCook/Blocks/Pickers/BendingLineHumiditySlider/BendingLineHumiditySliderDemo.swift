import SwiftUI

struct BendingLineHumiditySliderDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x070a26), height: 640) { BendingLineHumiditySlider(autoPlay: true) }
    }
}

#Preview {
    BendingLineHumiditySliderDemo()
}
