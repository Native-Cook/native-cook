import SwiftUI

struct FrostedDestinationCarouselDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xe6eaf0), height: 440) { FrostedDestinationCarousel(autoPlay: true) }
    }
}

#Preview {
    FrostedDestinationCarouselDemo()
}
