import SwiftUI

/// The menu's diet toggle on the whole screen, flipping between its two dishes by itself.
struct VegModeToggleDemo: View {
    var body: some View {
        VegModeToggle(autoPlay: true)
    }
}

#Preview {
    VegModeToggleDemo()
}
