import SwiftUI

struct ArcActivityPickerDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xF3F3F3), height: 700) { ArcActivityPicker(autoPlay: true) }
    }
}

#Preview {
    ArcActivityPickerDemo()
}
