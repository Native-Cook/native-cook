import SwiftUI

/// Gallery entry: plays the fixed 10 s pickup story from its resting pose.
struct ParcelLockerPickupDemo: View {
    var body: some View {
        ParcelLockerPickup(autoPlay: true)
    }
}

#Preview {
    ParcelLockerPickupDemo()
}
