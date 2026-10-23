import SwiftUI

struct TrailPhotoFanCardDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x1f9fc4), height: 620) { TrailPhotoFanCard(autoPlay: true) }
    }
}

#Preview {
    TrailPhotoFanCardDemo()
}
