import SwiftUI

struct HeatShapePickerDemo: View {
    var body: some View {
        HeatShapePicker(autoPlay: true)
    }
}

struct HeatShapePickerPreview: PreviewProvider {
    static var previews: some View { HeatShapePickerDemo() }
}
