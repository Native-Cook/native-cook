import SwiftUI

/// The department store's lift alone, full screen, riding to Home and back until touched.
struct ElevatorFloorNavDemo: View {
    var body: some View {
        ElevatorFloorNav(autoPlay: true)
            .background(Color(hex: 0x10221C).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    ElevatorFloorNavDemo()
}
