import SwiftUI

struct TravelStatStoryCardDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xe9e8e4), height: 560) { TravelStatStoryCard(autoPlay: true) }
    }
}

#Preview {
    TravelStatStoryCardDemo()
}
