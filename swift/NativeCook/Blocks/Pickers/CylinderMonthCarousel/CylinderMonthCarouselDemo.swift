import SwiftUI

struct CylinderMonthCarouselDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x1b1b1d), height: 540) { CylinderMonthCarousel(autoPlay: true) }
    }
}

#Preview {
    CylinderMonthCarouselDemo()
}
