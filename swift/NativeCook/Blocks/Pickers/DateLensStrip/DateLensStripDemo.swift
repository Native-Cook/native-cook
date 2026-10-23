import SwiftUI

struct DateLensStripDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xE6E6E8), height: 220) { DateLensStrip(autoPlay: true) }
    }
}

#Preview {
    DateLensStripDemo()
}
