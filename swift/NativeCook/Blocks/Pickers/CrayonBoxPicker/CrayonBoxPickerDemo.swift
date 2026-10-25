import SwiftUI

/// The colour picker alone, crayons being pulled and scribbled by themselves.
struct CrayonBoxPickerDemo: View {
    var body: some View {
        CrayonBoxPicker(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    CrayonBoxPickerDemo()
}
