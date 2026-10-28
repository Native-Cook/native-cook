import SwiftUI

/// The theme step on the whole screen, sweeping through four themes by itself.
struct KeypadThemeWaveDemo: View {
    var body: some View {
        KeypadThemeWave(autoPlay: true)
    }
}

#Preview {
    KeypadThemeWaveDemo()
}
