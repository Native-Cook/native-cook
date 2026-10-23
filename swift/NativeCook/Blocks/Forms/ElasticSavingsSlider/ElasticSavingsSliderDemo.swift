import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct ElasticSavingsSliderDemo: View {
    var body: some View {
        ElasticSavingsSlider(autoPlay: true)
    }
}

#Preview {
    ElasticSavingsSliderDemo()
}
