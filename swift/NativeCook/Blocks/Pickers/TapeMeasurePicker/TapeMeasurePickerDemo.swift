import SwiftUI

/// The height step of the onboarding, playing its 8 s loop, with the status bar hidden.
struct TapeMeasurePickerDemo: View {
    var body: some View {
        TapeMeasurePicker(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    TapeMeasurePickerDemo()
}
