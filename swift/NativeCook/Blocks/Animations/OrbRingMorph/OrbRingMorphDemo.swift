import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct OrbRingMorphDemo: View {
    var body: some View {
        OrbRingMorph(autoPlay: true)
    }
}

#Preview {
    OrbRingMorphDemo()
}
