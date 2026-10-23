import SwiftUI

struct BarcodeProgressDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xcccccc), height: 280) { BarcodeProgress(autoPlay: true) }
    }
}

#Preview {
    BarcodeProgressDemo()
}
