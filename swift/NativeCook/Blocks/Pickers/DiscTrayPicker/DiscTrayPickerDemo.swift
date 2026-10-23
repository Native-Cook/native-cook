import SwiftUI

struct DiscTrayPickerDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xefefef), height: 660) { DiscTrayPicker(autoPlay: true) }
    }
}

#Preview {
    DiscTrayPickerDemo()
}
