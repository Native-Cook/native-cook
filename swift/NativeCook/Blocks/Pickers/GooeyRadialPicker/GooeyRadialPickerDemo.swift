import SwiftUI

struct GooeyRadialPickerDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xe9eef2), height: 440) { GooeyRadialPicker(autoPlay: true) }
    }
}

#Preview {
    GooeyRadialPickerDemo()
}
