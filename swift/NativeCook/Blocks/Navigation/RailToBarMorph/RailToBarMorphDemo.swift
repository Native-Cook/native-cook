import SwiftUI

/// The block fills the screen and hides the status bar, so it is shown without a stage.
struct RailToBarMorphDemo: View {
    var body: some View {
        RailToBarMorph(autoPlay: true)
    }
}

#Preview {
    RailToBarMorphDemo()
}
