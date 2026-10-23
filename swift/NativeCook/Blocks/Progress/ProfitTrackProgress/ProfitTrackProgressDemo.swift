import SwiftUI

struct ProfitTrackProgressDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xfafafa), height: 320) { ProfitTrackProgress(autoPlay: true) }
    }
}

#Preview {
    ProfitTrackProgressDemo()
}
