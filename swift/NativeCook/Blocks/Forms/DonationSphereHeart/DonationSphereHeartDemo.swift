import SwiftUI

struct DonationSphereHeartDemo: View {
    var body: some View {
        DonationSphereHeart(autoPlay: true, renderTime: UserDefaults.standard.object(forKey: "block-time") as? Double)
    }
}
