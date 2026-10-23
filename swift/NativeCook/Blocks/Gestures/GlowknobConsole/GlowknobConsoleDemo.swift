import SwiftUI

/// The block on the whole screen: the console on its white glass stage, under
/// a dark status bar. What the original app set up around `GlowknobShowcaseScreen`
/// is done here.
struct GlowknobConsoleDemo: View {
  init() { GlowknobFonts.register() }

  var body: some View {
    GlowknobShowcaseScreen()
      .background(GlowknobTactile.page.color.ignoresSafeArea())
      .preferredColorScheme(.light)
  }
}

#Preview {
  GlowknobConsoleDemo()
}
