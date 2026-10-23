import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct SlingshotSendMoneyDemo: View {
    var body: some View {
        SlingshotSendMoney(autoPlay: true)
    }
}

#Preview {
    SlingshotSendMoneyDemo()
}
