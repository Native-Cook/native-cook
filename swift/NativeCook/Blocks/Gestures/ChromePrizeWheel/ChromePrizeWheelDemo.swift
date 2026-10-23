import SwiftUI

struct ChromePrizeWheelDemo: View {
    var autoPlay = true
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ChromePrizeWheelSurface(autoPlay: autoPlay, reduced: reduceMotion)
            .ignoresSafeArea()
            .statusBarHidden()
    }
}

#Preview {
    ChromePrizeWheelDemo()
}
