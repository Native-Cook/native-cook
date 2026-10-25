import SwiftUI

/// The delivery app on its own, its alert dropping in by parachute by itself.
struct ParachuteDropAlertDemo: View {
    var body: some View {
        ParachuteDropAlert(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    ParachuteDropAlertDemo()
}
