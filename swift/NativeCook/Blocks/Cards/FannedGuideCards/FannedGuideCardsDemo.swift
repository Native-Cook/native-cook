import SwiftUI

struct FannedGuideCardsDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xf0f2ef), height: 520) { FannedGuideCards(autoPlay: true) }
    }
}

#Preview {
    FannedGuideCardsDemo()
}
