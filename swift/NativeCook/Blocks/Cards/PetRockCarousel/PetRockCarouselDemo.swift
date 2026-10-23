import SwiftUI

/// The pet rocks alone on their paper, opening, scrolling and changing faces by themselves.
struct PetRockCarouselDemo: View {
    var body: some View {
        PetRockCarousel(autoPlay: true)
            .background(Color(hex: 0xedeae4).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    PetRockCarouselDemo()
}
