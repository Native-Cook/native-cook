import SwiftUI

/// The paint mixer on the whole screen, mixing by itself.
struct PaintMixerPickerDemo: View {
    var body: some View {
        PaintMixerPicker(autoPlay: true)
            .background(PaintMixerPickerArt.background.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    PaintMixerPickerDemo()
}
