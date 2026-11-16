import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct ThermalShapeMorphDemo: View {
    var body: some View {
        ThermalShapeMorph(autoPlay: true)
    }
}

#Preview {
    ThermalShapeMorphDemo()
}
