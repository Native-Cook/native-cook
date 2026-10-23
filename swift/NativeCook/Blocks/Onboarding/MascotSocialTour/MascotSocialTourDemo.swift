import SwiftUI

struct MascotSocialTourDemo: View {
    var body: some View {
        MascotSocialTour(
            autoPlay: true,
            renderTime: UserDefaults.standard.object(forKey: "block-time") as? Double
        )
    }
}

#Preview {
    MascotSocialTourDemo()
}
