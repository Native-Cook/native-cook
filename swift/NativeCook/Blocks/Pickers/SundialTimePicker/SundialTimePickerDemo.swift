import SwiftUI

/// The picker fills the whole screen; the sun is dragged to 15:30 and back by itself.
struct SundialTimePickerDemo: View {
    var body: some View {
        SundialTimePicker(autoPlay: true)
            .background(SundialTimePickerStyle.background.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    SundialTimePickerDemo()
}
