import SwiftUI

/// The block fills the screen and draws its own status bar, so it is shown without a stage.
struct WidgetGuideSheetDemo: View {
    var body: some View {
        WidgetGuideSheet(autoPlay: true)
    }
}

#Preview {
    WidgetGuideSheetDemo()
}
