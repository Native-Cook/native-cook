import SwiftUI

struct ColorRingPickerDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x121417), height: 460) { ColorRingPicker(autoPlay: true) }
    }
}

#Preview {
    ColorRingPickerDemo()
}
