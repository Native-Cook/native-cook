import SwiftUI

struct GlassBladeDrawDemo: View {
    var body: some View {
        GlassBladeDraw(autoPlay: true)
    }
}

/// A self-contained onboarding screen. Omitting autoPlay leaves the fan under touch control.
struct GlassBladeDraw: View {
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GlassBladeDrawSurface(autoPlay: autoPlay, reduced: reduceMotion)
            .ignoresSafeArea()
            .background(.black)
            .statusBarHidden()
    }
}

#Preview {
    GlassBladeDrawDemo()
}
