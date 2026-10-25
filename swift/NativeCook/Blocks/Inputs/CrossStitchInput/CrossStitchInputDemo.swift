import SwiftUI

/// The sampler screen alone, a name being stitched by itself.
struct CrossStitchInputDemo: View {
    var body: some View {
        CrossStitchInput(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    CrossStitchInputDemo()
}
