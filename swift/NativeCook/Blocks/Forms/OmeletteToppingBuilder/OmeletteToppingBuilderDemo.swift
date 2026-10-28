import SwiftUI

/// Step 2 of a breakfast order on the whole screen, folding three fillings into the omelette by itself.
struct OmeletteToppingBuilderDemo: View {
    var body: some View {
        OmeletteToppingBuilder(autoPlay: true)
    }
}

#Preview {
    OmeletteToppingBuilderDemo()
}
