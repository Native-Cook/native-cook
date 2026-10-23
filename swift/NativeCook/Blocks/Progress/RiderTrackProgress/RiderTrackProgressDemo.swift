import SwiftUI

struct RiderTrackProgressDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x02040d), height: 320) { RiderTrackProgress(autoPlay: true) }
    }
}

#Preview {
    RiderTrackProgressDemo()
}
