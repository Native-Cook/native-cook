import SwiftUI

/// The studio fills the whole screen; the clay is thrown into a vase by itself.
struct PotteryWheelShapeDemo: View {
    var body: some View {
        PotteryWheelShape(autoPlay: true)
            .background(PotteryWheelShapeStyle.background.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    PotteryWheelShapeDemo()
}
