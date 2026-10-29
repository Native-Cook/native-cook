import SwiftUI

/// Petalog's mood check-in on the whole screen, sliding through every mood by itself.
struct GlassMoodBloomDemo: View {
    var body: some View {
        GlassMoodBloom(autoPlay: true)
    }
}

#Preview {
    GlassMoodBloomDemo()
}
