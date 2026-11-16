import SwiftUI

struct BreathVolumeLungsDemo: View {
    static let loop = 8.5

    var body: some View {
        BreathVolumeLungs(autoPlay: true)
    }
}

#Preview {
    BreathVolumeLungsDemo()
}
