import SwiftUI

struct DestinationSwipeStackDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xf4f4f7), height: 620) { DestinationSwipeStack(autoPlay: true) }
    }
}

#Preview {
    DestinationSwipeStackDemo()
}
