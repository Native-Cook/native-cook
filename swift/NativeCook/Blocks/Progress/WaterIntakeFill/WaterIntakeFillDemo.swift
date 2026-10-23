import SwiftUI

struct WaterIntakeFillDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xb9cf7a), height: 480) { WaterIntakeFill(autoPlay: true) }
    }
}

#Preview {
    WaterIntakeFillDemo()
}
