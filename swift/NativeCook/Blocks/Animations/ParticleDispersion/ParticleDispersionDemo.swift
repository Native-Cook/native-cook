import SwiftUI

/// Camera capture drives a single 3.9-second effect; this block has no automatic recording loop.
struct ParticleDispersionDemo: View {
    var body: some View {
        ParticleDispersion().ignoresSafeArea()
    }
}
