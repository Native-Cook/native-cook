import SwiftUI

/// The block fills the screen, so it is shown without a stage.
struct LevelUpTakeoverDemo: View {
    var body: some View {
        LevelUpTakeover(autoPlay: true)
    }
}

#Preview {
    LevelUpTakeoverDemo()
}
