import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct WeightBreathingGlyphDemo: View {
    var body: some View {
        WeightBreathingGlyph(autoPlay: true)
    }
}

#Preview {
    WeightBreathingGlyphDemo()
}
