import SwiftUI

struct WeatherFanDialDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x2c2d32), height: 360) { WeatherFanDial(autoPlay: true) }
    }
}

#Preview {
    WeatherFanDialDemo()
}
