import SwiftUI

struct NeonCardFlightDemo: View {
    var body: some View {
        NeonCardFlight(autoPlay: true, renderTime: UserDefaults.standard.object(forKey: "block-time") as? Double)
    }
}

#Preview {
    NeonCardFlightDemo()
}
