import SwiftUI

struct ResortWeatherCardDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x060709), height: 660) { ResortWeatherCard(autoPlay: true) }
    }
}

#Preview {
    ResortWeatherCardDemo()
}
