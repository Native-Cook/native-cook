import SwiftUI

/// The games-room screen on the whole screen, throwing the dice by itself.
struct DiceRollPickerDemo: View {
    var body: some View {
        DiceRollPicker(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    DiceRollPickerDemo()
}
