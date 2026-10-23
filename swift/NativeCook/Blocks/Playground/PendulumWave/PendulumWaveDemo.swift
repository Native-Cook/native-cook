import SwiftUI

/// The pendulums alone on the whole screen, released and toured by themselves.
struct PendulumWaveDemo: View {
    var body: some View {
        PendulumWave(autoPlay: true)
            .background(PendulumWaveMetrics.background.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    PendulumWaveDemo()
}
