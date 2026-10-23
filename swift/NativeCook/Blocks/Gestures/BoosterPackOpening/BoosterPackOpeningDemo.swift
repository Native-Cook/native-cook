import SwiftUI

/// The block on the whole screen, playing by itself, with the status bar out of the way.
struct BoosterPackOpeningDemo: View {
  var body: some View {
    BoosterPackOpening(autoPlay: true)
      .background(Color(red: 0.071, green: 0.071, blue: 0.063).ignoresSafeArea())
      .statusBarHidden()
      .persistentSystemOverlays(.hidden)
  }
}

#Preview {
  BoosterPackOpeningDemo()
}
