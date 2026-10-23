import SwiftUI

struct ScaleArcWeightPickerDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xf4f3fa), height: 440) { ScaleArcWeightPicker(autoPlay: true) }
    }
}

#Preview {
    ScaleArcWeightPickerDemo()
}
